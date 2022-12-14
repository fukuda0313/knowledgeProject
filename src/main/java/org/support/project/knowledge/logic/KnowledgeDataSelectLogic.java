package org.support.project.knowledge.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.PropertyUtil;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.dao.CommentsDao;
import org.support.project.knowledge.dao.KnowledgeFilesDao;
import org.support.project.knowledge.dao.KnowledgeItemValuesDao;
import org.support.project.knowledge.dao.TargetsDao;
import org.support.project.knowledge.dao.TemplateMastersDao;
import org.support.project.knowledge.entity.CommentsEntity;
import org.support.project.knowledge.entity.KnowledgeFilesEntity;
import org.support.project.knowledge.entity.KnowledgeItemValuesEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.TemplateItemsEntity;
import org.support.project.knowledge.entity.TemplateMastersEntity;
import org.support.project.knowledge.vo.SearchKnowledgeParam;
import org.support.project.knowledge.vo.api.AttachedFile;
import org.support.project.knowledge.vo.api.Comment;
import org.support.project.knowledge.vo.api.Knowledge;
import org.support.project.knowledge.vo.api.KnowledgeDetail;
import org.support.project.knowledge.vo.api.Target;
import org.support.project.web.bean.LabelValue;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.NameId;
import org.support.project.web.entity.GroupsEntity;
import org.support.project.web.entity.UsersEntity;

@DI(instance = Instance.Singleton)
public class KnowledgeDataSelectLogic {
    /** LOG */
    private static final Log LOG = LogFactory.getLog(KnowledgeDataSelectLogic.class);
    /** Get instance */
    public static KnowledgeDataSelectLogic get() {
        return Container.getComp(KnowledgeDataSelectLogic.class);
    }
    
    private static final int SINGLE = 0;
    private static final int LIST = 1;
    
    
    /**
     * KnowledgesEntity ??? WebAPI??????????????????????????????
     * @param entity
     * @return
     */
    private Knowledge conv(KnowledgesEntity entity, int type) {
        if (entity == null) {
            return null;
        }
        Knowledge result = new Knowledge();
        PropertyUtil.copyPropertyValue(entity, result);
        
        // ????????????????????????????????????
        TemplateMastersEntity template = getTemplate(entity);
        result.setTemplate(template.getTypeName());
        
        // ??????
        String[] tags = entity.getTagNames().split(",");
        result.setTags(Arrays.asList(tags));
        
        // ????????????
        Target viewers = getViewers(entity);
        result.setViewers(viewers);
        
        // 1???????????????????????????????????????????????????
        if (SINGLE == type) {
            KnowledgeDetail detail = new KnowledgeDetail();
            PropertyUtil.copyPropertyValue(result, detail);
            
            // ??????????????????????????????????????????????????????
            List<LabelValue> templateItems = getTemplateItems(entity, template);
            detail.setTemplateItems(templateItems);
            
            // ????????????
            List<Comment> comments = getComments(entity);
            detail.setComments(comments);
            
            // ??????????????????
            List<AttachedFile> attachedFiles = getAttachedFiles(entity);
            detail.setAttachments(attachedFiles);
            
            // ???????????????
            Target editors = getEditors(entity);
            detail.setEditors(editors);
            
            
            return detail;
        }
        return result;
    }
    /**
     * ???????????????????????????
     * @param entity
     * @return
     */
    private List<AttachedFile> getAttachedFiles(KnowledgesEntity entity) {
        List<AttachedFile> attachedFiles = new ArrayList<>();
        List<KnowledgeFilesEntity> filesEntities = KnowledgeFilesDao.get().selectOnKnowledgeId(entity.getKnowledgeId());
        for (KnowledgeFilesEntity knowledgeFilesEntity : filesEntities) {
            AttachedFile file = new AttachedFile();
            PropertyUtil.copyPropertyValue(knowledgeFilesEntity, file);
            attachedFiles.add(file);
        }
        return attachedFiles;
    }
    /**
     * ??????????????????
     * @param entity
     * @return
     */
    private List<Comment> getComments(KnowledgesEntity entity) {
        List<Comment> comments = new ArrayList<>();
        List<CommentsEntity> commentsEntities = CommentsDao.get().selectOnKnowledgeId(entity.getKnowledgeId());
        for (CommentsEntity commentsEntity : commentsEntities) {
            Comment comment = new Comment();
            PropertyUtil.copyPropertyValue(commentsEntity, comment);
            comments.add(comment);
        }
        return comments;
    }
    /**
     * ????????????????????????????????????????????????
     * @param entity
     * @param template
     * @return
     */
    private List<LabelValue> getTemplateItems(KnowledgesEntity entity, TemplateMastersEntity template) {
        List<KnowledgeItemValuesEntity> values = KnowledgeItemValuesDao.get().selectOnKnowledgeId(entity.getKnowledgeId());
        List<TemplateItemsEntity> items = template.getItems();
        List<LabelValue> templateItems = new ArrayList<>();
        for (KnowledgeItemValuesEntity val : values) {
            for (TemplateItemsEntity item : items) {
                if (val.getItemNo().equals(item.getItemNo())) {
                    item.setItemValue(val.getItemValue());
                    templateItems.add(new LabelValue(
                            item.getItemName(),
                            val.getItemValue()));
                    break;
                }
            }
        }
        return templateItems;
    }
    /**
     * ????????????????????????
     * @param entity
     * @return
     */
    private Target getEditors(KnowledgesEntity entity) {
        Target editors = new Target();
        List<NameId> listGroups = new ArrayList<>();
        List<NameId> listUsers = new ArrayList<>();
        editors.setGroups(listGroups);
        editors.setUsers(listUsers);
        TargetsDao targetsDao = TargetsDao.get();
        List<GroupsEntity> groups = targetsDao.selectEditorGroupsOnKnowledgeId(entity.getKnowledgeId());
        for (GroupsEntity groupsEntity : groups) {
            NameId group = new NameId(groupsEntity.getGroupName(), String.valueOf(groupsEntity.getGroupId()));
            listGroups.add(group);
        }
        List<UsersEntity> users = targetsDao.selectEditorUsersOnKnowledgeId(entity.getKnowledgeId());
        for (UsersEntity usersEntity : users) {
            NameId user = new NameId(usersEntity.getUserName(), String.valueOf(usersEntity.getUserId()));
            listUsers.add(user);
        }
        return editors;
    }
    /**
     * ?????????????????????
     * @param entity
     * @return
     */
    private Target getViewers(KnowledgesEntity entity) {
        Target viewers = new Target();
        if (entity.getPublicFlag().intValue() == KnowledgeLogic.PUBLIC_FLAG_PROTECT) {
            List<NameId> groupViewers = new ArrayList<>();
            List<NameId> userViewers = new ArrayList<>();
            viewers.setGroups(groupViewers);
            viewers.setUsers(userViewers);
            TargetsDao targetsDao = TargetsDao.get();
            List<GroupsEntity> groups = targetsDao.selectGroupsOnKnowledgeId(entity.getKnowledgeId());
            for (GroupsEntity groupsEntity : groups) {
                NameId group = new NameId(groupsEntity.getGroupName(), String.valueOf(groupsEntity.getGroupId()));
                groupViewers.add(group);
            }
            List<UsersEntity> users = targetsDao.selectUsersOnKnowledgeId(entity.getKnowledgeId());
            for (UsersEntity usersEntity : users) {
                NameId user = new NameId(usersEntity.getUserName(), String.valueOf(usersEntity.getUserId()));
                userViewers.add(user);
            }
        }
        return viewers;
    }
    /**
     * ????????????????????????????????????
     * @param entity
     * @return
     */
    private TemplateMastersEntity getTemplate(KnowledgesEntity entity) {
        int typeId = entity.getTypeId();
        TemplateMastersEntity template = TemplateMastersDao.get().selectWithItems(typeId);
        if (template == null) {
            // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            typeId = TemplateLogic.TYPE_ID_KNOWLEDGE;
            template = TemplateMastersDao.get().selectWithItems(typeId);
        }
        return template;
    }
    
    /**
     * Knowledge???????????????1????????????WebAPI??????????????????
     * @param knowledgeId
     * @param loginedUser
     * @return
     */
    public Knowledge select(long knowledgeId, LoginedUser loginedUser) {
        KnowledgesEntity entity = KnowledgeLogic.get().selectWithTags(knowledgeId, loginedUser);
        if (entity == null) {
            return null;
        }
        Knowledge result = conv(entity, SINGLE);
        return result;
    }
    /**
     * Knowledge????????????????????????WebAPI??????????????????
     * @param param
     * @return
     * @throws Exception
     */
    public List<Knowledge> selectList(SearchKnowledgeParam param) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("get knowledge list. [params] " + PropertyUtil.reflectionToString(param));
        }
        String [] templates = {param.getTemplate()}; // TODO ???????????????????????????????????????????????????

        List<Knowledge> results = new ArrayList<>();
        List<KnowledgesEntity> entities = KnowledgeLogic.get().searchKnowledge(
                param.getKeyword(),
                param.getTags(),
                param.getGroups(),
                null,
                templates,
                param.getLoginedUser(),
                param.getOffset(),
                param.getLimit());
        List<String> ids = new ArrayList<>();
        for (KnowledgesEntity entity : entities) {
            Knowledge result = conv(entity, LIST);
            results.add(result);
            ids.add(String.valueOf(result.getKnowledgeId()));
        }
        List<KnowledgesEntity> dbs = KnowledgeLogic.get().getKnowledges(ids, param.getLoginedUser());
        Map<Long, KnowledgesEntity> idMap = new HashedMap<>();
        for (KnowledgesEntity knowledgesEntity : dbs) {
            idMap.put(knowledgesEntity.getKnowledgeId(), knowledgesEntity);
        }
        for (Knowledge knowledge : results) {
            if (idMap.containsKey(knowledge.getKnowledgeId())) {
                // Title???????????????????????????
                knowledge.setTitle(idMap.get(knowledge.getKnowledgeId()).getTitle());
            }
        }
        return results;
    }

}
