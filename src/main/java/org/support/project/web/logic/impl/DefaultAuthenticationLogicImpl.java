package org.support.project.web.logic.impl;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.support.project.aop.Aspect;
import org.support.project.common.config.ConfigLoader;
import org.support.project.common.config.INT_FLAG;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.Compare;
import org.support.project.common.util.PasswordUtil;
import org.support.project.common.util.RandomUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.web.bean.LdapInfo;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.UserSecret;
import org.support.project.web.config.AppConfig;
import org.support.project.web.config.CommonWebParameter;
import org.support.project.web.config.WebConfig;
import org.support.project.web.dao.LdapConfigsDao;
import org.support.project.web.dao.UserAliasDao;
import org.support.project.web.dao.UsersDao;
import org.support.project.web.entity.LdapConfigsEntity;
import org.support.project.web.entity.UserAliasEntity;
import org.support.project.web.entity.UsersEntity;
import org.support.project.web.exception.AuthenticateException;
import org.support.project.web.logic.AddUserProcess;
import org.support.project.web.logic.LdapLogic;
import org.support.project.web.logic.UserLogic;

import net.arnx.jsonic.JSON;

@DI(instance = Instance.Singleton)
public class DefaultAuthenticationLogicImpl extends AbstractAuthenticationLogic<LoginedUser> {
    /** ?????? */
    private static final Log LOG = LogFactory.getLog(DefaultAuthenticationLogicImpl.class);
    
    private int cookieMaxAge = -1; // ???????????????
    private String cookieEncryptKey = "";
    private boolean cookieSecure = true;
    
    /**
     * Cookie???????????????????????????????????????
     * @param cookieMaxAge cookieMaxAge
     * @param cookieEncryptKey cookieEncryptKey
     * @param cookieSecure cookieSecure
     */
    public void initCookie(int cookieMaxAge, String cookieEncryptKey, boolean cookieSecure) {
        this.cookieMaxAge = cookieMaxAge;
        this.cookieEncryptKey = cookieEncryptKey;
        this.cookieSecure = cookieSecure;
    }
    
    /**
     * ??????????????????????????????????????????
     * 
     * @param req request
     * @param res response
     * @throws AuthenticateException AuthenticateException
     */
    public void setCookie(HttpServletRequest req, HttpServletResponse res)
            throws AuthenticateException {
        try {
            // ???????????????????????????????????????(?????????)
            Cookie[] cookies = req.getCookies();
            if (cookies != null && cookieMaxAge > 0 && StringUtils.isNotEmpty(cookieEncryptKey)) {
                LoginedUser user = getSession(req);
                
                UserSecret secret = new UserSecret();
                secret.setUserKey(user.getLoginUser().getUserKey());
                secret.setUserName(user.getLoginUser().getUserName());
                secret.setEmail(user.getLoginUser().getMailAddress());
                
                String json = JSON.encode(secret);
                json = PasswordUtil.encrypt(json, cookieEncryptKey);
    
                Cookie cookie = new Cookie(CommonWebParameter.LOGIN_USER_KEY, json);
                cookie.setPath(req.getContextPath() + "/");
                cookie.setMaxAge(cookieMaxAge);
                cookie.setSecure(cookieSecure);
                res.addCookie(cookie);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new AuthenticateException(e);
        }
    }
    
    /**
     * Cookie??????????????????????????????????????????????????????
     * 
     * @param req request
     * @param res response
     * @return result
     */
    public boolean cookieLogin(HttpServletRequest req, HttpServletResponse res) {
        // ???????????????????????????????????????
        HttpSession session = req.getSession();
        if (Boolean.TRUE.equals(session.getAttribute("COOKIE_LOGIN_CHECK"))) {
            // ??????Cookie?????????????????????????????????????????????????????????
            return false;
        }

        Cookie[] cookies = req.getCookies();
        if (cookies != null && cookieMaxAge > 0 && StringUtils.isNotEmpty(cookieEncryptKey)) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(CommonWebParameter.LOGIN_USER_KEY)) {
                    String json = cookie.getValue();
                    try {
                        json = PasswordUtil.decrypt(json, cookieEncryptKey);
                        UserSecret user = JSON.decode(json, UserSecret.class);
                        
                        UsersEntity entity =  UsersDao.get().selectOnLowerUserKey(user.getUserKey());
                        if (entity == null) {
                            return false;
                        }
                        if (!user.getUserKey().toLowerCase().equals(entity.getUserKey().toLowerCase())
                                || !user.getUserName().equals(entity.getUserName())
                                || !StringUtils.equals(user.getEmail(), entity.getMailAddress())) {
                            LOG.info("Cookie of LOGIN_USER_KEY is invalid.");
                            return false;
                        }
                        
                        
                        LOG.debug(user.getUserKey() + " is Login(from cookie).");
                        setSession(user.getUserKey(), req, res); //??????????????????LoginUser?????????

                        // Cookie????????????
                        UserSecret secret = new UserSecret();
                        secret.setUserKey(user.getUserKey());
                        secret.setUserName(user.getUserName());
                        secret.setEmail(user.getEmail());
                        json = JSON.encode(user);
                        json = PasswordUtil.encrypt(json, cookieEncryptKey);

                        cookie = new Cookie(CommonWebParameter.LOGIN_USER_KEY, json);
                        cookie.setPath(req.getContextPath() + "/");
                        cookie.setMaxAge(cookieMaxAge);
                        cookie.setSecure(cookieSecure);
                        res.addCookie(cookie);

                        // ??????????????????
                        return true;
                    } catch (Exception e) {
                        // ???????????????
                        LOG.trace("error cookieLogin.", e);
                    }
                }
            }
        }
        session.setAttribute("COOKIE_LOGIN_CHECK", Boolean.TRUE);
        return false;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.support.project.web.logic.impl.AbstractAuthenticationLogic#auth(java.lang.String, java.lang.String)
     */
    @Override
    @Aspect(advice = org.support.project.ormapping.transaction.Transaction.class)
    public int auth(String userId, String password) throws AuthenticateException {
        initLogic();
        // Ldap??????????????????????????????Ldap?????????????????????
        LdapConfigsDao dao = LdapConfigsDao.get();
        List<LdapConfigsEntity> ldaps = dao.selectAll();
        for (LdapConfigsEntity config : ldaps) {
            try {
                LdapLogic ldapLogic = LdapLogic.get();
                LdapInfo ldapInfo = ldapLogic.auth(config, userId, password);
                if (ldapInfo != null) {
                    // Ldap????????????
                    UserAliasEntity alias = UserAliasDao.get().selectOnAliasKey(config.getSystemName(), userId);
                    if (alias != null) {
                        // Alias???????????????
                        UsersDao usersDao = UsersDao.get();
                        UsersEntity usersEntity = usersDao.selectOnKey(alias.getUserId());
                        if (usersEntity == null) {
                            return Integer.MIN_VALUE;
                        } else {
                            if (Compare.equal(alias.getUserInfoUpdate(), INT_FLAG.ON.getValue())) {
                                // ??????????????????????????????????????????????????????????????????
                                updateUser(userId, password, ldapInfo, usersDao, usersEntity);
                            }
                        }
                        return usersEntity.getUserId();
                    } else {
                        UsersDao usersDao = UsersDao.get();
                        
                        // ??????????????????????????????????????????????????????
                        UsersEntity usersEntity = usersDao.selectOnLowerUserKey(userId);
                        if (usersEntity == null) {
                            usersEntity = addUser(userId, password, ldapInfo);
                            // ???????????????????????????
                            if (StringUtils.isNotEmpty(AppConfig.get().getAddUserProcess())) {
                                AddUserProcess process = Container.getComp(AppConfig.get().getAddUserProcess(), AddUserProcess.class);
                                process.addUserProcess(usersEntity.getUserKey());
                            }
                        } else {
                            updateUser(userId, password, ldapInfo, usersDao, usersEntity);
                        }
                        // ????????????Alias?????????
                        alias = new UserAliasEntity();
                        alias.setUserInfoUpdate(INT_FLAG.ON.getValue());
                        alias.setUserId(usersEntity.getUserId());
                        alias.setAuthKey(config.getSystemName());
                        alias.setAliasKey(userId);
                        alias.setAliasName(ldapInfo.getName().toLowerCase());
                        alias.setAliasMail(ldapInfo.getMail());
                        UserAliasDao.get().save(alias);
                        return usersEntity.getUserId();
                    }
                }
            } catch (LdapException | IOException e) {
                throw new AuthenticateException(e);
            }
        }
        
        // DB????????????
        try {
            if (StringUtils.isEmpty(password)) {
                return Integer.MIN_VALUE;
            }
            UsersDao usersDao = UsersDao.get();
            UsersEntity usersEntity = usersDao.selectOnUserKey(userId);
            AppConfig config = ConfigLoader.load(AppConfig.APP_CONFIG, AppConfig.class);
            if (usersEntity != null && 
                    (usersEntity.getAuthLdap() == null || usersEntity.getAuthLdap().intValue() == INT_FLAG.OFF.getValue())
            ) {
                String hash = PasswordUtil.getStretchedPassword(password, usersEntity.getSalt(), config.getHashIterations());
                if (usersEntity.getPassword().equals(hash)) {
                    return usersEntity.getUserId();
                }
            }
            return Integer.MIN_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new AuthenticateException(e);
        }
    }

    /**
     * Ldap???????????????????????????????????????????????? ??????ID???
     * 
     * @param userId
     * @param ldapInfo
     * @param usersDao
     * @param usersEntity
     */
    private void updateUser(String userId, String password, LdapInfo ldapInfo, UsersDao usersDao, UsersEntity usersEntity) {
        // ??????????????????????????????????????????????????????Ldap???????????????????????????????????????Knowledge?????????????????????????????????
        boolean change = false;
        if (StringUtils.isNotEmpty(ldapInfo.getName())) {
            if (!ldapInfo.getName().equals(usersEntity.getUserName())) {
                usersEntity.setUserName(ldapInfo.getName());
                change = true;
            }
        }
        if (StringUtils.isNotEmpty(ldapInfo.getMail())) {
            if (StringUtils.isEmailAddress(ldapInfo.getMail()) && !ldapInfo.getMail().equals(usersEntity.getMailAddress())) {
                usersEntity.setMailAddress(ldapInfo.getMail());
                change = true;
            }
        }
        if (usersEntity.getAuthLdap() == null || usersEntity.getAuthLdap().intValue() != INT_FLAG.ON.getValue()) {
            // ??????Knowledge????????????????????????????????????Ldap???????????????ID?????????????????????
            // ????????????????????????????????????????????????
            // TODO ???????????????????????????????????????????????????????????????????????????
            usersEntity.setAuthLdap(INT_FLAG.ON.getValue());
            change = true;
        }
        if (change) {
            usersEntity.setPassword(RandomUtil.randamGen(32));
            usersDao.save(usersEntity);
            LOG.debug("Change User info on Ldap login. [user]" + userId);
        }
    }

    /**
     * Ldap??????????????????????????????????????????
     * 
     * @param userId
     * @param password
     * @param ldapInfo
     */
    private UsersEntity addUser(String userId, String password, LdapInfo ldapInfo) {
        UsersEntity usersEntity;
        // Ldap??????????????????????????????????????????????????????????????????
        usersEntity = new UsersEntity();
        usersEntity.setUserKey(ldapInfo.getId());
        if (StringUtils.isNotEmpty(ldapInfo.getName())) {
            usersEntity.setUserName(ldapInfo.getName());
        } else {
            usersEntity.setUserName(ldapInfo.getId());
        }
        if (StringUtils.isNotEmpty(ldapInfo.getMail())) {
            if (StringUtils.isEmailAddress(ldapInfo.getMail())) {
                usersEntity.setMailAddress(ldapInfo.getMail());
            }
        }
        usersEntity.setAuthLdap(INT_FLAG.ON.getValue());
        usersEntity.setAdmin(ldapInfo.isAdmin());
        // usersEntity.setPassword(password);
        usersEntity.setPassword(RandomUtil.randamGen(24)); // Ldap????????????????????????????????????????????????????????????????????????????????????

        List<String> roles = new ArrayList<String>();
        roles.add(WebConfig.ROLE_USER);
        if (ldapInfo.isAdmin()) {
            roles.add(WebConfig.ROLE_ADMIN);
        }
        usersEntity.setPassword(RandomUtil.randamGen(32));
        usersEntity = UserLogic.get().insert(usersEntity, roles.toArray(new String[0]));
        LOG.info("Add User on first Ldap login. [user]" + userId);
        return usersEntity;
    }

}
