package org.support.project.knowledge.control.protect;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.support.project.common.bean.ValidateError;
import org.support.project.common.config.INT_FLAG;
import org.support.project.common.exception.ParseException;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.DateUtils;
import org.support.project.common.util.PropertyUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.UserConfig;
import org.support.project.knowledge.control.KnowledgeControlBase;
import org.support.project.knowledge.dao.CommentsDao;
import org.support.project.knowledge.dao.DraftKnowledgesDao;
import org.support.project.knowledge.dao.KnowledgesDao;
import org.support.project.knowledge.dao.StockKnowledgesDao;
import org.support.project.knowledge.dao.TagsDao;
import org.support.project.knowledge.dao.TemplateMastersDao;
import org.support.project.knowledge.entity.CommentsEntity;
import org.support.project.knowledge.entity.DraftKnowledgesEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.StockKnowledgesEntity;
import org.support.project.knowledge.entity.TagsEntity;
import org.support.project.knowledge.entity.TemplateItemsEntity;
import org.support.project.knowledge.entity.TemplateMastersEntity;
import org.support.project.knowledge.logic.GroupLogic;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.logic.TargetLogic;
import org.support.project.knowledge.logic.TemplateLogic;
import org.support.project.knowledge.logic.UploadedFileLogic;
import org.support.project.knowledge.logic.activity.Activity;
import org.support.project.knowledge.logic.activity.ActivityLogic;
import org.support.project.knowledge.vo.KnowledgeData;
import org.support.project.knowledge.vo.Stock;
import org.support.project.knowledge.vo.UploadFile;
import org.support.project.web.bean.LabelValue;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.boundary.Boundary;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.config.HttpMethod;
import org.support.project.web.config.MessageStatus;
import org.support.project.web.control.service.Get;
import org.support.project.web.control.service.Post;
import org.support.project.web.dao.UserConfigsDao;
import org.support.project.web.entity.GroupsEntity;
import org.support.project.web.entity.UserConfigsEntity;
import org.support.project.web.exception.AuthenticateException;
import org.support.project.web.exception.InvalidParamException;

import net.arnx.jsonic.JSON;

@DI(instance = Instance.Prototype)
public class KnowledgeControl extends KnowledgeControlBase {
    /** ?????? */
    private static final Log LOG = LogFactory.getLog(KnowledgeControl.class);

    private KnowledgeLogic knowledgeLogic = KnowledgeLogic.get();
    private UploadedFileLogic fileLogic = UploadedFileLogic.get();

    /**
     * ???????????????????????????
     * 
     * @return
     */
    @Get(publishToken = "knowledge")
    public Boundary view_add() {
        // ??????????????????????????????????????????????????????
        setViewParam();
        setAttributeForEditPage();

        setAttribute("typeId", TemplateLogic.TYPE_ID_KNOWLEDGE); // ?????????
        
        String offset = super.getParam("offset", String.class);
        if (StringUtils.isEmpty(offset)) {
            offset = "0";
        }
        setAttribute("offset", offset);
        
        // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        String groupId = super.getParam("group", String.class);
        if (StringUtils.isNotEmpty(groupId)) {
            GroupsEntity group = GroupLogic.get().getGroup(new Integer(groupId), getLoginedUser());
            if (group == null) {
                return sendError(HttpStatus.SC_403_FORBIDDEN, "");
            }

            String[] groupIds = { TargetLogic.ID_PREFIX_GROUP + groupId };
            List<LabelValue> targets = TargetLogic.get().selectTargets(groupIds);
            setAttribute("publicFlag", KnowledgeLogic.PUBLIC_FLAG_PROTECT);
            setAttribute("groups", targets);
            setAttribute("editors", targets);
        }
        
        if (getAttribute("publicFlag") == null) {
            UserConfigsEntity publicFlag = UserConfigsDao.get().physicalSelectOnKey(
                    UserConfig.DEFAULT_PUBLIC_FLAG, AppConfig.get().getSystemName(), getLoginUserId());
            if (publicFlag != null) {
                setAttribute("publicFlag", publicFlag.getConfigValue());
                UserConfigsEntity targets = UserConfigsDao.get().physicalSelectOnKey(
                        UserConfig.DEFAULT_TARGET, AppConfig.get().getSystemName(), getLoginUserId());
                if (targets != null) {
                    if (StringUtils.isNotEmpty(targets.getConfigValue())) {
                        String[] targetKeys = targets.getConfigValue().split(",");
                        List<LabelValue> viewers = TargetLogic.get().selectTargets(targetKeys);
                        setAttribute("groups", viewers);
                    }
                }
            } else {
                setAttribute("publicFlag", KnowledgeLogic.PUBLIC_FLAG_PRIVATE);
            }
        }
        
        return forward("edit.jsp");
    }

    /**
     * ???????????????????????????
     * 
     * @return
     * @throws InvalidParamException
     */
    @Get(publishToken = "knowledge")
    public Boundary view_edit() throws InvalidParamException {
        // ??????????????????????????????????????????????????????
        setViewParam();
        setAttributeForEditPage();

        String offset = super.getParam("offset", String.class);
        if (StringUtils.isEmpty(offset)) {
            offset = "0";
        }
        setAttribute("offset", offset);

        Long knowledgeId = super.getPathLong();
        KnowledgesEntity entity = knowledgeLogic.selectWithTags(knowledgeId, getLoginedUser());
        if (entity == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        
        // ???????????????????????????????????????????????????????????????????????????
        DraftKnowledgesEntity draft = DraftKnowledgesDao.get().selectOnKnowledgeAndUser(knowledgeId, getLoginUserId());
        if (draft != null) {
            super.setDraftInfo(draft);
        } else {
            // ????????????????????????
            LoginedUser loginedUser = super.getLoginedUser();

            // ???????????????????????????????????????????????????
            List<UploadFile> files = fileLogic.selectOnKnowledgeIdWithoutCommentFiles(knowledgeId, getRequest().getContextPath());
            setAttribute("files", files);

            // ?????????????????????????????????
            List<LabelValue> groups = TargetLogic.get().selectTargetsViewOnKnowledgeId(knowledgeId, loginedUser);
            setAttribute("groups", groups);

            // ???????????????
            List<LabelValue> editors = TargetLogic.get().selectEditorsViewOnKnowledgeId(knowledgeId, loginedUser);
            setAttribute("editors", editors);

            boolean edit = knowledgeLogic.isEditor(loginedUser, entity, editors);
            if (!edit) {
                setAttribute("edit", false);
                addMsgWarn("knowledge.edit.noaccess");
                // return forward("/open/knowledge/view.jsp");
                return devolution(HttpMethod.get, "open.Knowledge/view", String.valueOf(knowledgeId));
            }
            setAttributeOnProperty(entity);
        }
        
        return forward("edit.jsp");
    }

    /**
     * ???????????? API???????????????????????????????????????????????????
     * 
     * @return
     * @throws Exception
     * @throws ParseException
     */
    private Boundary add(KnowledgesEntity entity) throws Exception, ParseException {
        List<ValidateError> errors = entity.validate();
        if (!errors.isEmpty()) {
            // ???????????????
            return sendValidateError(errors);
        }

        TemplateMastersEntity template = TemplateMastersDao.get().selectWithItems(entity.getTypeId());
        List<TemplateItemsEntity> items = template.getItems();
        for (TemplateItemsEntity item : items) {
            String itemValue = super.getParam("item_" + item.getItemNo());
            if (itemValue.startsWith("[") && itemValue.endsWith("]")) {
                itemValue = itemValue.substring(1, itemValue.length() - 1);
                item.setItemValue(itemValue);
            } else {
                item.setItemValue(itemValue);
            }
        }
        
        KnowledgeData data = KnowledgeData.create(
                entity, super.getParam("groups"), super.getParam("editors"), super.getParam("tagNames"),
                getParam("files", String[].class), getParam("draftId", Long.class), template);
        
        LOG.trace("save");

        KnowledgesEntity insertedEntity = knowledgeLogic.insert(data, super.getLoginedUser(), false);
        
        return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK,
                String.valueOf(insertedEntity.getKnowledgeId()), "message.success.insert");
    }

    /**
     * ???????????? API???????????????????????????????????????????????????
     * 
     * @return
     * @throws Exception
     */
    private Boundary update(KnowledgesEntity entity) throws Exception {
        List<ValidateError> errors = entity.validate();
        if (!errors.isEmpty()) {
            // ???????????????
            return sendValidateError(errors);
        }

        TemplateMastersEntity template = TemplateMastersDao.get().selectWithItems(entity.getTypeId());
        List<TemplateItemsEntity> items = template.getItems();
        for (TemplateItemsEntity item : items) {
            item.setItemValue("");
            String[] itemValues = super.getParam("item_" + item.getItemNo(), String[].class);
            if (itemValues != null && itemValues.length == 1) {
                String itemValue = itemValues[0];
                if (itemValue.startsWith("[") && itemValue.endsWith("]")) {
                    itemValue = itemValue.substring(1, itemValue.length() - 1);
                    item.setItemValue(itemValue);
                } else {
                    item.setItemValue(itemValue);
                }
            } else if (itemValues != null && itemValues.length > 1) {
                for (String itemValue : itemValues) {
                    StringBuilder value = new StringBuilder();
                    if (!StringUtils.isEmpty(item.getItemValue())) {
                        value.append(item.getItemValue()).append(",");
                    }
                    if (itemValue.startsWith("[") && itemValue.endsWith("]")) {
                        itemValue = itemValue.substring(1, itemValue.length() - 1);
                        value.append(itemValue);
                    } else {
                        value.append(itemValue);
                    }
                    item.setItemValue(value.toString());
                }
            }
            
        }

        KnowledgeData data = KnowledgeData.create(
                entity, super.getParam("groups"), super.getParam("editors"), super.getParam("tagNames"),
                getParam("files", String[].class), getParam("draftId", Long.class), template);
        
        KnowledgesDao dao = Container.getComp(KnowledgesDao.class);
        KnowledgesEntity check = dao.selectOnKey(entity.getKnowledgeId());
        if (check == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        // ????????????????????????
        if (!knowledgeLogic.isEditor(super.getLoginedUser(), check, data.getEditors())) {
            setAttribute("edit", false);
            addMsgWarn("knowledge.edit.noaccess");
            // return devolution(HttpMethod.get, "open.Knowledge/view", String.valueOf(entity.getKnowledgeId()));

            errors = new ArrayList<>();
            errors.add(new ValidateError("knowledge.edit.noaccess"));
            return sendValidateError(errors);
        }
        
        // ????????????????????????????????????????????????????????????????????????????????????????????????
        if ("true".equals(super.getAttribute("notUpdateTimeline", "false"))) {
            data.setDonotUpdateTimeline(true);
        }
        if (!StringUtils.isEmpty(getParam("updateContent")) && getParam("updateContent").toLowerCase().equals("true")) {
            // ????????????????????????????????????????????????
            data.setUpdateContent(true);
            if (!data.isDonotUpdateTimeline()) {
                data.setNotifyUpdate(true);
            }
            LOG.debug("??????????????????????????????");
        } else {
            // ???????????????????????????
            data.setUpdateContent(false);
            LOG.debug("???????????????????????????");
            if (check.getNotifyStatus() == null || check.getNotifyStatus().intValue() == INT_FLAG.OFF.getValue()) {
                if (check.getPublicFlag().intValue() == KnowledgeLogic.PUBLIC_FLAG_PRIVATE &&
                        check.getPublicFlag().intValue() != data.getKnowledge().getPublicFlag().intValue()) {
                    // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                    data.setNotifyUpdate(true);
                    LOG.debug("???????????????????????????????????????????????????????????????????????????????????????");
                }
            }
        }
        // ????????????
        KnowledgesEntity updatedEntity = knowledgeLogic.update(data, super.getLoginedUser(), false);
        
        if (data.isUpdateContent()) {
            return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK,
                    String.valueOf(updatedEntity.getKnowledgeId()), "knowledge.edit.update.content");
        } else {
            return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK,
                    String.valueOf(updatedEntity.getKnowledgeId()), "knowledge.edit.update.meta");
        }
    }

    /**
     * Knowledge?????????
     * @param entity Knowledge information
     * @return Boundary
     * @throws Exception Exception
     */
    @Post(subscribeToken = "knowledge", checkReqToken = true)
    public Boundary save(KnowledgesEntity entity) throws Exception {
        try {
            if (entity.getPoint() == null) {
                entity.setPoint(0); // ?????????
            }
            if (entity.getKnowledgeId() != null && entity.getKnowledgeId() >= 1) {
                return update(entity);
            } else {
                return add(entity);
            }
        } catch(InvalidParamException e) {
            return send(e.getMessageResult());
        }
    }

    /**
     * ???????????????
     * @param entity Knowledge information
     * @return Boundary
     * @throws Exception Exception
     */
    @Post(subscribeToken = "knowledge", checkReqToken = true)
    public Boundary draft() throws Exception {
        DraftKnowledgesEntity draft = getParamOnProperty(DraftKnowledgesEntity.class);
        draft.setAccesses(super.getParam("groups"));
        draft.setEditors(super.getParam("editors"));
        draft.setTagNames(super.getParam("tagNames"));
        String[] files = getParam("files", String[].class);
        
        TemplateMastersEntity template = TemplateMastersDao.get().selectWithItems(draft.getTypeId());
        List<TemplateItemsEntity> items = template.getItems();
        for (TemplateItemsEntity item : items) {
            String itemValue = super.getParam("item_" + item.getItemNo());
            if (itemValue.startsWith("[") && itemValue.endsWith("]")) {
                itemValue = itemValue.substring(1, itemValue.length() - 1);
                item.setItemValue(itemValue);
            } else {
                item.setItemValue(itemValue);
            }
        }
        try {
            draft = knowledgeLogic.draft(draft, template, files, super.getLoginedUser());
            return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK, String.valueOf(draft.getDraftId()), "message.success.save");
        } catch (AuthenticateException e) {
            // ?????????????????????
            return sendMsg(MessageStatus.Warning, HttpStatus.SC_403_FORBIDDEN, null, "knowledge.edit.noaccess");
        }
    }
    
    
    /**
     * ?????????????????????
     * 
     * @return
     * @throws Exception
     */
    @Post(subscribeToken = "knowledge", checkReqToken = true)
    public Boundary delete() throws Exception {
        // ??????????????????????????????????????????????????????
        setViewParam();

        LOG.trace("validate");
        KnowledgesDao dao = Container.getComp(KnowledgesDao.class);
        String id = getParam("knowledgeId");
        if (!StringUtils.isInteger(id)) {
            // ????????????ID???????????????????????????
            // return sendError(HttpStatus.SC_400_BAD_REQUEST, null);
            addMsgError("knowledge.delete.none");
            // return super.devolution("open.knowledge/list");
            return forward("/commons/errors/server_error.jsp");
        }

        List<TagsEntity> tagitems = TagsDao.get().selectAll();
        setAttribute("tagitems", tagitems);

        List<TemplateMastersEntity> templates = TemplateLogic.get().selectAll();
        setAttribute("templates", templates);

        Long knowledgeId = new Long(id);
        KnowledgesEntity check = dao.selectOnKey(knowledgeId);
        if (check == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        List<LabelValue> editors = TargetLogic.get().selectEditorsOnKnowledgeId(knowledgeId);
        if (!knowledgeLogic.isEditor(super.getLoginedUser(), check, editors)) {
            setAttribute("edit", false);
            addMsgWarn("knowledge.edit.noaccess");
            // return forward("/open/knowledge/view.jsp");
            return devolution(HttpMethod.get, "open.Knowledge/view", String.valueOf(knowledgeId));
        }
        LOG.trace("save");
        knowledgeLogic.delete(knowledgeId);

        addMsgSuccess("message.success.delete");
        return super.devolution(HttpMethod.get, "open.Knowledge/list");
    }

    /**
     * ????????????????????????????????????
     * 
     * @return
     * @throws InvalidParamException
     */
    @Get(publishToken = "knowledge")
    public Boundary view() throws InvalidParamException {
        // ??????????????????????????????????????????????????????
        setViewParam();

        Long knowledgeId = super.getPathLong(Long.valueOf(-1));
        return super.redirect(getRequest().getContextPath() + "/open.knowledge/view/" + knowledgeId);
    }

    /**
     * ??????????????????
     * 
     * @return
     * @throws Exception
     */
    @Post(subscribeToken = "knowledge")
    public Boundary comment() throws Exception {
        // ??????????????????????????????????????????????????????
        String params = setViewParam();
        Long knowledgeId = super.getPathLong(Long.valueOf(-1));
        // String comment = super.sanitize(getParam("addcomment"));

        List<Long> fileNos = new ArrayList<Long>();
        Object obj = getParam("files", Object.class);
        if (obj != null) {
            if (obj instanceof String) {
                String string = (String) obj;
                if (StringUtils.isLong(string)) {
                    fileNos.add(new Long(string));
                }
            } else if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> strings = (List<String>) obj;
                for (String string : strings) {
                    if (StringUtils.isLong(string)) {
                        fileNos.add(new Long(string));
                    }
                }
            }
        }

        String comment = getParam("addcomment");

        // ??????????????????
        if (StringUtils.isEmpty(comment)) {
            addMsgWarn("errors.required", "Comment");

            // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            List<UploadFile> files = fileLogic.selectOnFileNos(fileNos, getRequest().getContextPath());
            Iterator<UploadFile> iterator = files.iterator();
            while (iterator.hasNext()) {
                UploadFile uploadFile = iterator.next();
                if (uploadFile.getKnowlegeId() != null) {
                    // ??????????????????????????????????????????????????????????????????????????????????????????????????????
                    iterator.remove();
                }
            }
            setAttribute("comment_files", files);
            return super.devolution(HttpMethod.get, "open.Knowledge/view", String.valueOf(knowledgeId));
        }
        KnowledgeLogic.get().saveComment(knowledgeId, comment, fileNos, getLoginedUser());
        return super.redirect(getRequest().getContextPath() + "/open.knowledge/view/" + knowledgeId + params);
    }

    /**
     * ????????????????????????????????????????????????????????????
     * 
     * @return
     * @throws InvalidParamException
     */
    @Get(publishToken = "knowledge")
    public Boundary view_targets() throws InvalidParamException {
        Long knowledgeId = super.getPathLong(Long.valueOf(-1));
        List<LabelValue> groups = TargetLogic.get().selectTargetsOnKnowledgeId(knowledgeId);
        return super.send(groups);
    }

    /**
     * ?????????????????????????????????
     * 
     * @return
     * @throws InvalidParamException
     */
    @Get(publishToken = "knowledge")
    public Boundary edit_comment() throws InvalidParamException {
        Long commentNo = super.getPathLong(Long.valueOf(-1));
        CommentsDao commentsDao = CommentsDao.get();
        CommentsEntity commentsEntity = commentsDao.selectOnKey(commentNo);

        if (commentsEntity == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }

        // // ????????????????????????????????????????????????????????????
        // KnowledgeLogic knowledgeLogic = KnowledgeLogic.get();
        // KnowledgesEntity entity = knowledgeLogic.select(commentsEntity.getKnowledgeId(), getLoginedUser());
        // if (entity == null) {
        // return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT FOUND");
        // }

        // ????????????????????????????????????????????????????????????????????? or ???????????????????????? or ?????????????????????
        LoginedUser loginedUser = super.getLoginedUser();
        if (loginedUser == null) {
            // ????????????????????????????????????????????????????????????
            return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }
        if (!loginedUser.isAdmin() && loginedUser.getUserId().intValue() != commentsEntity.getInsertUser().intValue()) {
            KnowledgesEntity check = KnowledgesDao.get().selectOnKey(commentsEntity.getKnowledgeId());
            if (check == null) {
                return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
            }
            List<LabelValue> editors = TargetLogic.get().selectEditorsOnKnowledgeId(commentsEntity.getKnowledgeId());
            if (!knowledgeLogic.isEditor(super.getLoginedUser(), check, editors)) {
                return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
            }
        }

        // ???????????????????????????????????????????????????
        List<UploadFile> files = fileLogic.selectOnKnowledgeId(commentsEntity.getKnowledgeId(), getRequest().getContextPath());
        List<UploadFile> commentFiles = new ArrayList<>();
        for (UploadFile uploadFile : files) {
            if (commentsEntity.getCommentNo().equals(uploadFile.getCommentNo())) {
                commentFiles.add(uploadFile);
            }
        }
        setAttribute("comment_files", commentFiles);

        setAttributeOnProperty(commentsEntity);
        return forward("edit_comment.jsp");
    }

    /**
     * ?????????????????????
     * 
     * @return
     * @throws Exception
     */
    @Post(subscribeToken = "knowledge")
    public Boundary update_comment() throws Exception {
        List<Long> fileNos = new ArrayList<Long>();
        Object obj = getParam("files", Object.class);
        if (obj != null) {
            if (obj instanceof String) {
                String string = (String) obj;
                if (StringUtils.isLong(string)) {
                    fileNos.add(new Long(string));
                }
            } else if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> strings = (List<String>) obj;
                for (String string : strings) {
                    if (StringUtils.isLong(string)) {
                        fileNos.add(new Long(string));
                    }
                }
            }
        }
        // ????????????????????????????????????????????????????????????
        List<UploadFile> files = fileLogic.selectOnFileNos(fileNos, getRequest().getContextPath());
        Iterator<UploadFile> iterator = files.iterator();
        while (iterator.hasNext()) {
            UploadFile uploadFile = iterator.next();
            if (uploadFile.getKnowlegeId() != null) {
                // ??????????????????????????????????????????????????????????????????????????????????????????????????????
                iterator.remove();
            }
        }
        setAttribute("comment_files", files);

        CommentsEntity commentsEntity = getParamOnProperty(CommentsEntity.class);

        CommentsDao commentsDao = CommentsDao.get();
        CommentsEntity db = commentsDao.selectOnKey(commentsEntity.getCommentNo());

        if (db == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        // ????????????????????????????????????????????????????????????????????? or ???????????????????????? or ????????????????????????????????????
        LoginedUser loginedUser = super.getLoginedUser();
        if (loginedUser == null) {
            // ????????????????????????????????????????????????????????????
            return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }
        KnowledgesEntity check = KnowledgesDao.get().selectOnKey(db.getKnowledgeId());
        if (check == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        List<LabelValue> editors = TargetLogic.get().selectEditorsOnKnowledgeId(db.getKnowledgeId());
        if (!loginedUser.isAdmin()) {
            if (loginedUser.getUserId().intValue() != db.getInsertUser().intValue()
                    && !knowledgeLogic.isEditor(super.getLoginedUser(), check, editors))
                return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }

        // ??????????????????
        if (StringUtils.isEmpty(commentsEntity.getComment())) {
            addMsgWarn("errors.required", "Comment");
            return super.devolution(HttpMethod.get, "/protect.knowledge/edit_comment", String.valueOf(commentsEntity.getCommentNo()));
        }
        db.setComment(commentsEntity.getComment());
        KnowledgeLogic.get().updateComment(db, fileNos, getLoginedUser());
        setAttributeOnProperty(db);

        addMsgSuccess("message.success.update");
        //return devolution(HttpMethod.get, "/open.Knowledge/view", String.valueOf(db.getKnowledgeId()));
        return super.redirect(getRequest().getContextPath() + "/open.knowledge/view/" + db.getKnowledgeId());
    }

    /**
     * ?????????????????????
     * 
     * @return
     * @throws Exception
     */
    @Get(subscribeToken = "knowledge")
    public Boundary delete_comment() throws Exception {
        Long commentNo = super.getPathLong(Long.valueOf(-1));
        CommentsDao commentsDao = CommentsDao.get();
        CommentsEntity db = commentsDao.selectOnKey(commentNo);

        if (db == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        // ????????????????????????????????????????????????????????????????????? or ???????????????????????? or ????????????????????????????????????
        LoginedUser loginedUser = super.getLoginedUser();
        if (loginedUser == null) {
            // ????????????????????????????????????????????????????????????
            return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }
        KnowledgesEntity check = KnowledgesDao.get().selectOnKey(db.getKnowledgeId());
        if (check == null) {
            return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
        }
        List<LabelValue> editors = TargetLogic.get().selectEditorsOnKnowledgeId(db.getKnowledgeId());

        if (!loginedUser.isAdmin()) {
            if (loginedUser.getUserId().intValue() != db.getInsertUser().intValue()
                    && !knowledgeLogic.isEditor(super.getLoginedUser(), check, editors))
                return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }

        KnowledgeLogic.get().deleteComment(db, getLoginedUser());
        
        addMsgSuccess("message.success.delete.target", getResource("label.comment"));
        setAttribute("comment", null);
        return devolution(HttpMethod.get, "open.Knowledge/view", String.valueOf(db.getKnowledgeId()));
    }

    /**
     * ????????????????????????????????????
     * 
     * @return
     * @throws IOException
     * @throws InvalidParamException
     */
    @Post(subscribeToken = "knowledge")
    public Boundary stock() throws IOException, InvalidParamException {
        Long knowledgeId = getPathLong();
        if (LOG.isTraceEnabled()) {
            LOG.trace(knowledgeId);
        }

        List<Stock> stocks = new ArrayList<>();
        BufferedReader reader = getRequest().getReader();
        try {
            List<Map<String, String>> json = JSON.decode(reader);
            for (Map<String, String> map : json) {
                Stock stock = new Stock();
                Iterator<String> keys = map.keySet().iterator();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = map.get(key);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(key + " = " + value + "  (" + value.getClass().getName() + ")");
                    }
                    Object val = PropertyUtil.convValue(value.toString(), PropertyUtil.getPropertyType(stock, key));
                    PropertyUtil.setPropertyValue(stock, key, val);
                }
                stocks.add(stock);
                if (LOG.isTraceEnabled()) {
                    LOG.trace(PropertyUtil.reflectionToString(stock));
                }
            }
        } finally {
            reader.close();
        }

        StockKnowledgesDao dao = StockKnowledgesDao.get();
        for (Stock stock : stocks) {
            StockKnowledgesEntity entity = new StockKnowledgesEntity();
            entity.setStockId(stock.getStockId());
            entity.setKnowledgeId(knowledgeId);
            entity.setComment(stock.getDescription());
            if (stock.getStocked()) {
                dao.save(entity);
            } else {
                dao.physicalDelete(entity);
            }
        }
        ActivityLogic.get().processActivity(Activity.KNOWLEDGE_STOCK, getLoginedUser(), DateUtils.now(),
                KnowledgesDao.get().selectOnKey(knowledgeId));
        
        return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK, "saved", "message.success.save");
    };

    /**
     * ??????????????????????????????
     * 
     * @return
     * @throws IOException
     * @throws InvalidParamException
     */
    @Post(subscribeToken = "knowledge")
    public Boundary collapse() throws IOException, InvalidParamException {
        Long commentNo = getParam("commentNo", Long.class);
        Integer collapse = getParam("collapse", Integer.class);

        CommentsDao commentsDao = CommentsDao.get();
        CommentsEntity db = commentsDao.selectOnKey(commentNo);

        // ????????????????????????????????????????????????????????????????????? or ???????????????????????? or ?????????????????????
        LoginedUser loginedUser = super.getLoginedUser();
        if (loginedUser == null) {
            // ????????????????????????????????????????????????????????????
            return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
        }
        if (!loginedUser.isAdmin() && loginedUser.getUserId().intValue() != db.getInsertUser().intValue()) {
            KnowledgesEntity check = KnowledgesDao.get().selectOnKey(db.getKnowledgeId());
            if (check == null) {
                return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
            }
            List<LabelValue> editors = TargetLogic.get().selectEditorsOnKnowledgeId(db.getKnowledgeId());
            if (!knowledgeLogic.isEditor(super.getLoginedUser(), check, editors)) {
                return sendError(HttpStatus.SC_403_FORBIDDEN, "FORBIDDEN");
            }
        }

        // ?????????????????????
        db.setCommentStatus(collapse);
        commentsDao.physicalUpdate(db); // ????????????????????????????????????

        if (collapse == 1) {
            return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK, String.valueOf(commentNo), "knowledge.view.comment.collapse.on");
        } else {
            return sendMsg(MessageStatus.Success, HttpStatus.SC_200_OK, String.valueOf(commentNo), "knowledge.view.comment.collapse.off");
        }
    }

}
