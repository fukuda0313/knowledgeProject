package org.support.project.knowledge.logic.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.support.project.aop.Aspect;
import org.support.project.common.config.INT_FLAG;
import org.support.project.common.config.Resources;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.RandomUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.dao.KnowledgesDao;
import org.support.project.knowledge.dao.LikesDao;
import org.support.project.knowledge.dao.NotifyConfigsDao;
import org.support.project.knowledge.dao.NotifyQueuesDao;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.LikesEntity;
import org.support.project.knowledge.entity.MailLocaleTemplatesEntity;
import org.support.project.knowledge.entity.NotifyConfigsEntity;
import org.support.project.knowledge.entity.NotifyQueuesEntity;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.logic.MailLogic;
import org.support.project.knowledge.logic.NotificationLogic;
import org.support.project.knowledge.logic.NotifyLogic;
import org.support.project.knowledge.logic.notification.webhook.KnowledgeLikedWebHookNotification;
import org.support.project.knowledge.vo.notification.LikeInsert;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.MessageResult;
import org.support.project.web.dao.MailConfigsDao;
import org.support.project.web.dao.MailsDao;
import org.support.project.web.dao.NotificationsDao;
import org.support.project.web.dao.UsersDao;
import org.support.project.web.entity.MailConfigsEntity;
import org.support.project.web.entity.MailsEntity;
import org.support.project.web.entity.NotificationsEntity;
import org.support.project.web.entity.UsersEntity;

import net.arnx.jsonic.JSON;

/**
 * ???????????????????????????????????????
 * @author koda
 */
@DI(instance = Instance.Prototype)
public class LikeInsertNotification extends AbstractQueueNotification implements DesktopNotification {
    /** ?????? */
    private static final Log LOG = LogFactory.getLog(LikeInsertNotification.class);
    /** ???????????????????????? */
    public static LikeInsertNotification get() {
        return Container.getComp(LikeInsertNotification.class);
    }

    /** like id what is sended */
    private List<Long> sendedLikeKnowledgeIds = new ArrayList<>();
    
    /** Added LikesEntity */
    private LikesEntity like;
    public LikesEntity getLike() {
        return like;
    }
    public void setLike(LikesEntity like) {
        this.like = like;
    }

    @Override
    public void insertNotifyQueue() {
        NotifyQueuesEntity entity = new NotifyQueuesEntity();
        entity.setHash(RandomUtil.randamGen(30));
        entity.setType(TYPE_KNOWLEDGE_LIKE);
        entity.setId(like.getNo());
        
        NotifyQueuesDao notifyQueuesDao = NotifyQueuesDao.get();
        NotifyQueuesEntity exist = notifyQueuesDao.selectOnTypeAndId(entity.getType(), entity.getId());
        if (exist == null) {
            notifyQueuesDao.insert(entity);
        }
    }
    
    @Override
    @Aspect(advice = org.support.project.ormapping.transaction.Transaction.class)
    public void notify(NotifyQueuesEntity notifyQueue) throws Exception {
        LikesDao likesDao = LikesDao.get();
        LikesEntity like = likesDao.selectOnKey(notifyQueue.getId());
        if (null == like) {
            LOG.warn("Like record not found. id: " + notifyQueue.getId());
            return;
        }
        
        // Webhook??????
        KnowledgeLikedWebHookNotification webhook = KnowledgeLikedWebHookNotification.get();
        webhook.init(like);
        webhook.saveWebhookData();
        
        // ??????????????????????????????????????????????????????????????????
        KnowledgesDao knowledgesDao = KnowledgesDao.get();
        KnowledgesEntity knowledge = knowledgesDao.selectOnKey(like.getKnowledgeId());
        if (null == knowledge) {
            LOG.warn("Knowledge record not found. id: " + notifyQueue.getId());
            return;
        }
        
        if (sendedLikeKnowledgeIds.contains(knowledge.getKnowledgeId())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Knowledge [" + knowledge.getKnowledgeId() + "] ");
            }
            return;
        } else {
            sendedLikeKnowledgeIds.add(knowledge.getKnowledgeId());
        }

        UsersDao usersDao = UsersDao.get();
        UsersEntity likeUser = usersDao.selectOnKey(like.getInsertUser());
        
        // ??????????????????
        UsersEntity user = usersDao.selectOnKey(knowledge.getInsertUser());
        if (user != null) {
            NotifyConfigsDao notifyConfigsDao = NotifyConfigsDao.get();
            NotifyConfigsEntity notifyConfigsEntity = notifyConfigsDao.selectOnKey(user.getUserId());
            // ?????????????????????????????????????????????????????????????????????
            if (notifyConfigsEntity != null && INT_FLAG.flagCheck(notifyConfigsEntity.getMyItemLike())) {
                // ??????????????????
                NotificationsEntity notification = insertNotificationOnLikeMyItem(knowledge, like, likeUser);
                // ??????????????????????????????
                NotificationLogic.get().insertUserNotification(notification, user);
                
                // ???????????????
                Locale locale = user.getLocale();
                MailLocaleTemplatesEntity template = MailLogic.get().load(locale, MailLogic.NOTIFY_INSERT_LIKE_MYITEM);
                sendLikeMail(knowledge, likeUser, user, template);
            }
        }
    }
    
    private NotificationsEntity insertNotificationOnLikeMyItem(KnowledgesEntity knowledge, LikesEntity like, UsersEntity likeUser) {
        // ?????????????????????
        NotificationsEntity notification = new NotificationsEntity();
        notification.setTitle(MailLogic.NOTIFY_INSERT_LIKE_MYITEM);
        
        LikeInsert info = new LikeInsert();
        info.setKnowledgeId(knowledge.getKnowledgeId());
        info.setKnowledgeTitle(knowledge.getTitle());
        info.setUpdateUser(knowledge.getUpdateUserName());
        if (likeUser != null) {
            info.setLikeInsertUser(likeUser.getUserName());
        } else {
            info.setLikeInsertUser("anonymous");
        }
        
        notification.setContent(JSON.encode(info));
        notification = NotificationsDao.get().insert(notification);
        return notification;

    }

    /**
     * ??????????????????????????????????????????
     * @param like
     * @param knowledge
     * @param likeUser
     * @param user
     * @param template
     * @throws Exception 
     */
    public void sendLikeMail(KnowledgesEntity knowledge, UsersEntity likeUser, UsersEntity user,
            MailLocaleTemplatesEntity template) throws Exception {
        MailConfigsDao mailConfigsDao = MailConfigsDao.get();
        MailConfigsEntity mailConfigsEntity = mailConfigsDao.selectOnKey(AppConfig.get().getSystemName());
        if (mailConfigsEntity == null) {
            // ???????????????????????????????????????????????????????????????????????????
            LOG.info("mail config is not exists.");
            return;
        }
        if (!StringUtils.isEmailAddress(user.getMailAddress())) {
            // ???????????????????????????????????????????????????????????????????????????
            LOG.warn("mail targget [" + user.getMailAddress() + "] is wrong.");
            return;
        }

        MailsEntity mailsEntity = new MailsEntity();
        String mailId = idGen("Notify");
        mailsEntity.setMailId(mailId);
        mailsEntity.setStatus(MailLogic.MAIL_STATUS_UNSENT);
        mailsEntity.setToAddress(user.getMailAddress());
        mailsEntity.setToName(user.getUserName());
        
        String title = template.getTitle();
        title = title.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
        title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(knowledge.getTitle(), 80));
        mailsEntity.setTitle(title);
        String contents = template.getContent();
        contents = contents.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
        contents = contents.replace("{KnowledgeTitle}", knowledge.getTitle());
        contents = contents.replace("{Contents}", MailLogic.get().getMailContent(knowledge.getContent()));
        if (likeUser != null) {
            contents = contents.replace("{LikeInsertUser}", likeUser.getUserName());
        } else {
            contents = contents.replace("{LikeInsertUser}", "");
        }
        contents = contents.replace("{URL}", NotifyLogic.get().makeURL(knowledge.getKnowledgeId()));
        
        mailsEntity.setContent(contents);
        if (LOG.isDebugEnabled()) {
            LOG.debug("News email has been registered. [type] Like added. [knowledge]" + knowledge.getKnowledgeId().toString()
                    + " [target] " + user.getMailAddress());
        }
        MailsDao.get().insert(mailsEntity);
    }
    

    @Override
    public void convNotification(NotificationsEntity notificationsEntity, LoginedUser loginedUser, TARGET target) {
        LikeInsert info = JSON.decode(notificationsEntity.getContent(), LikeInsert.class);
        MailLocaleTemplatesEntity template = MailLogic.get().load(loginedUser.getLocale(), notificationsEntity.getTitle());
        
        String title = template.getTitle();
        title = title.replace("{KnowledgeId}", String.valueOf(info.getKnowledgeId()));
        title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(info.getKnowledgeTitle(), 80));
        notificationsEntity.setTitle(title);
        
        if (target == TARGET.detail) {
            String contents = template.getContent();
            contents = contents.replace("{KnowledgeId}", String.valueOf(info.getKnowledgeId()));
            contents = contents.replace("{KnowledgeTitle}", info.getKnowledgeTitle());
            contents = contents.replace("{User}", info.getUpdateUser());
            contents = contents.replace("{URL}", NotifyLogic.get().makeURL(info.getKnowledgeId()));
            KnowledgesEntity entity = KnowledgeLogic.get().select(info.getKnowledgeId(), loginedUser);
            if (entity != null) {
                contents = contents.replace("{Contents}", entity.getContent());
            } else {
                contents = contents.replace("{Contents}", "");
            }
            contents = contents.replace("{LikeInsertUser}", info.getLikeInsertUser());
            notificationsEntity.setContent(contents);
        }
    }
    @Override
    public MessageResult getMessage(LoginedUser loginuser, Locale locale) {
        NotifyConfigsDao dao = NotifyConfigsDao.get();
        NotifyConfigsEntity entity = dao.selectOnKey(loginuser.getUserId());
        if (!NotifyLogic.get().flagCheck(entity.getNotifyDesktop())) {
            // ?????????????????????????????????
            return null;
        }
        KnowledgesDao knowledgesDao = KnowledgesDao.get();
        KnowledgesEntity knowledge = knowledgesDao.selectOnKey(like.getKnowledgeId());

        if (NotifyLogic.get().flagCheck(entity.getMyItemLike()) && knowledge.getInsertUser().intValue() == loginuser.getUserId().intValue()) {
            // ????????????????????????????????????????????????????????????????????????
            MessageResult messageResult = new MessageResult();
            messageResult.setMessage(Resources.getInstance(locale).getResource("knowledge.notify.msg.desktop.myitem.like",
                    String.valueOf(knowledge.getKnowledgeId())));
            messageResult.setResult(NotifyLogic.get().makeURL(knowledge.getKnowledgeId())); // Knowledge???????????????
            return messageResult;
        }

        return null;
    }

}
