package org.support.project.knowledge.logic.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.support.project.aop.Aspect;
import org.support.project.common.config.Resources;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.RandomUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.NotifyType;
import org.support.project.knowledge.dao.CommentsDao;
import org.support.project.knowledge.dao.KnowledgesDao;
import org.support.project.knowledge.dao.NotifyConfigsDao;
import org.support.project.knowledge.dao.NotifyQueuesDao;
import org.support.project.knowledge.entity.CommentsEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.MailLocaleTemplatesEntity;
import org.support.project.knowledge.entity.NotifyConfigsEntity;
import org.support.project.knowledge.entity.NotifyQueuesEntity;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.logic.MailLogic;
import org.support.project.knowledge.logic.NotificationLogic;
import org.support.project.knowledge.logic.NotifyCommentLogic;
import org.support.project.knowledge.logic.NotifyLogic;
import org.support.project.knowledge.logic.notification.webhook.CommentInsertWebhookNotification;
import org.support.project.knowledge.vo.notification.CommentInsert;
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
 * ????????????????????????????????????????????????????????????
 * @author koda
 */
@DI(instance = Instance.Prototype)
public class CommentInsertNotification extends AbstractQueueNotification implements DesktopNotification {
    /** ?????? */
    private static final Log LOG = LogFactory.getLog(CommentInsertNotification.class);
    /** ???????????????????????? */
    public static CommentInsertNotification get() {
        return Container.getComp(CommentInsertNotification.class);
    }

    /** Added CommentsEntity */
    private CommentsEntity comment;
    public CommentsEntity getComment() {
        return comment;
    }
    public void setComment(CommentsEntity comment) {
        this.comment = comment;
    }

    /** knowledge id what is sended */
    private List<Long> sendedCommentKnowledgeIds = new ArrayList<>();
    
    @Override
    public void insertNotifyQueue() {
        NotifyQueuesEntity entity = new NotifyQueuesEntity();
        entity.setHash(RandomUtil.randamGen(30));
        entity.setType(TYPE_KNOWLEDGE_COMMENT);
        entity.setId(comment.getCommentNo());
        
        NotifyQueuesDao notifyQueuesDao = NotifyQueuesDao.get();
        NotifyQueuesEntity exist = notifyQueuesDao.selectOnTypeAndId(entity.getType(), entity.getId());
        if (exist == null) {
            notifyQueuesDao.insert(entity);
        }
    }
    
    
    @Override
    @Aspect(advice = org.support.project.ormapping.transaction.Transaction.class)
    public void notify(NotifyQueuesEntity notifyQueue) throws Exception {
        CommentsDao commentsDao = CommentsDao.get();
        CommentsEntity comment = commentsDao.selectOnKey(notifyQueue.getId());
        if (null == comment) {
            LOG.warn("Comment record not found. id: " + notifyQueue.getId());
            return;
        }

        KnowledgesDao knowledgesDao = KnowledgesDao.get();
        KnowledgesEntity knowledge = knowledgesDao.selectOnKey(comment.getKnowledgeId());
        if (null == knowledge) {
            LOG.warn("Knowledge record not found. id: " + notifyQueue.getId());
            return;
        }

        if (sendedCommentKnowledgeIds.contains(knowledge.getKnowledgeId())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Knowledge [" + knowledge.getKnowledgeId() + "] ");
            }
            return;
        } else {
            sendedCommentKnowledgeIds.add(knowledge.getKnowledgeId());
        }
        
        // Webhook??????
        CommentInsertWebhookNotification webhook = CommentInsertWebhookNotification.get();
        webhook.init(comment, knowledge);
        webhook.saveWebhookData();

        UsersDao usersDao = UsersDao.get();
        UsersEntity commentUser = usersDao.selectOnKey(comment.getInsertUser());
        
        // ??????????????????
        UsersEntity user = NotifyCommentLogic.get().getInsertUserOnComment(NotifyType.Mail, comment, knowledge);
        if (user != null) {
            // ????????????????????????????????????
            NotificationsEntity notification = insertNotificationOnComentInsert(knowledge, comment, commentUser,
                    MailLogic.NOTIFY_INSERT_COMMENT_MYITEM);
            // ??????????????????????????????
            NotificationLogic.get().insertUserNotification(notification, user);
            
            Locale locale = user.getLocale();
            MailLocaleTemplatesEntity template = MailLogic.get().load(locale, MailLogic.NOTIFY_INSERT_COMMENT_MYITEM);
            sendCommentMail(comment, knowledge, commentUser, user, template);
        }
        // ?????????????????????????????????????????????????????????????????????????????????
        NotificationsEntity notification = insertNotificationOnComentInsert(knowledge, comment, commentUser,
                MailLogic.NOTIFY_INSERT_COMMENT);
        List<UsersEntity> users = NotifyCommentLogic.get().getTargetUsersOnComment(NotifyType.Mail, comment, knowledge);
        for (UsersEntity target : users) {
            // ??????????????????????????????
            NotificationLogic.get().insertUserNotification(notification, target);
            
            // ????????????????????????
            Locale locale = target.getLocale();
            MailLocaleTemplatesEntity template = MailLogic.get().load(locale, MailLogic.NOTIFY_INSERT_COMMENT);
            sendCommentMail(comment, knowledge, commentUser, target, template);
        }
    }
    
    /**
     * ????????????????????????????????????
     * @param knowledge
     * @param comment
     * @param commentUser
     * @param notifyInsertCommentMyitem 
     * @return
     */
    private NotificationsEntity insertNotificationOnComentInsert(KnowledgesEntity knowledge, CommentsEntity comment, UsersEntity commentUser,
            String template) {
        // ?????????????????????
        NotificationsEntity notification = new NotificationsEntity();
        notification.setTitle(template);
        
        CommentInsert info = new CommentInsert();
        info.setKnowledgeId(knowledge.getKnowledgeId());
        info.setKnowledgeTitle(knowledge.getTitle());
        info.setUpdateUser(knowledge.getUpdateUserName());
        
        info.setCommentContents(comment.getComment());
        info.setCommentInsertUser(commentUser.getUserName());
        
        notification.setContent(JSON.encode(info));
        notification = NotificationsDao.get().insert(notification);
        return notification;
    }


    /**
     * ??????????????????????????????????????????????????????
     * @param comment ?????????????????????
     * @param knowledge ?????????????????????
     * @param commentUser ?????????????????????????????????????????????
     * @param user ?????????????????????
     * @throws Exception 
     */
    private void sendCommentMail(CommentsEntity comment, KnowledgesEntity knowledge, UsersEntity commentUser,
            UsersEntity user, MailLocaleTemplatesEntity template)
            throws Exception {
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
        if (commentUser != null) {
            contents = contents.replace("{CommentInsertUser}", commentUser.getUserName());
        } else {
            contents = contents.replace("{CommentInsertUser}", "");
        }
        contents = contents.replace("{CommentContents}", MailLogic.get().getMailContent(comment.getComment()));
        contents = contents.replace("{URL}", NotifyLogic.get().makeURL(knowledge.getKnowledgeId()));

        mailsEntity.setContent(contents);
        if (LOG.isDebugEnabled()) {
            LOG.debug("News email has been registered. [type] comment added. [knowledge]" + knowledge.getKnowledgeId().toString()
                    + " [target] " + user.getMailAddress());
        }
        MailsDao.get().insert(mailsEntity);
    }
    

    

    @Override
    public void convNotification(NotificationsEntity notificationsEntity, LoginedUser loginedUser, TARGET target) {
        CommentInsert update = JSON.decode(notificationsEntity.getContent(), CommentInsert.class);
        MailLocaleTemplatesEntity template = MailLogic.get().load(loginedUser.getLocale(), notificationsEntity.getTitle());
        
        String title = template.getTitle();
        title = title.replace("{KnowledgeId}", String.valueOf(update.getKnowledgeId()));
        title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(update.getKnowledgeTitle(), 80));
        notificationsEntity.setTitle(title);
        
        if (target == TARGET.detail) {
            String contents = template.getContent();
            contents = contents.replace("{KnowledgeId}", String.valueOf(update.getKnowledgeId()));
            contents = contents.replace("{KnowledgeTitle}", update.getKnowledgeTitle());
            contents = contents.replace("{User}", update.getUpdateUser());
            contents = contents.replace("{URL}", NotifyLogic.get().makeURL(update.getKnowledgeId()));
            KnowledgesEntity entity = KnowledgeLogic.get().select(update.getKnowledgeId(), loginedUser);
            if (entity != null) {
                contents = contents.replace("{Contents}", entity.getContent());
            } else {
                contents = contents.replace("{Contents}", "");
            }
            contents = contents.replace("{CommentInsertUser}", update.getCommentInsertUser());
            contents = contents.replace("{CommentContents}", update.getCommentContents());
            
            notificationsEntity.setContent(contents);
        }
    }
    @Override
    public MessageResult getMessage(LoginedUser loginuser, Locale locale) {
        NotifyConfigsDao dao = NotifyConfigsDao.get();
        NotifyConfigsEntity entity = dao.selectOnKey(loginuser.getUserId()); // ??????????????????????????????????????????????????????
        if (!NotifyLogic.get().flagCheck(entity.getNotifyDesktop())) {
            // ?????????????????????????????????
            return null;
        }
        
        KnowledgesDao knowledgesDao = KnowledgesDao.get();
        KnowledgesEntity knowledge = knowledgesDao.selectOnKey(comment.getKnowledgeId());
        // ??????????????????
        UsersEntity user = NotifyCommentLogic.get().getInsertUserOnComment(NotifyType.Desktop, comment, knowledge);
        if (user != null) {
            if (user.getUserId().intValue() == loginuser.getUserId().intValue()) {
                // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                MessageResult messageResult = new MessageResult();
                messageResult.setMessage(Resources.getInstance(locale).getResource("knowledge.notify.msg.desktop.myitem.comment",
                        StringUtils.abbreviate(knowledge.getTitle(), 80)));
                messageResult.setResult(NotifyLogic.get().makeURL(knowledge.getKnowledgeId())); // Knowledge???????????????
                return messageResult;
            }
        }
        // ?????????????????????????????????????????????????????????????????????????????????
        List<UsersEntity> users = NotifyCommentLogic.get().getTargetUsersOnComment(NotifyType.Desktop, comment, knowledge);
        for (UsersEntity target : users) {
            if (target.getUserId().intValue() == loginuser.getUserId().intValue()) {
                // ??????????????????????????????????????????????????????????????????
                MessageResult messageResult = new MessageResult();
                messageResult.setMessage(
                        Resources.getInstance(locale).getResource("knowledge.notify.msg.desktop.to.comment",
                                StringUtils.abbreviate(knowledge.getTitle(), 80)));
                messageResult.setResult(NotifyLogic.get().makeURL(knowledge.getKnowledgeId())); // Knowledge???????????????
                return messageResult;
            }
        }
        return null;
    }

}
