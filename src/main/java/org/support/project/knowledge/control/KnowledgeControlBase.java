package org.support.project.knowledge.control;

import java.util.ArrayList;
import java.util.List;

import org.support.project.common.util.HtmlUtils;
import org.support.project.common.util.StringUtils;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.LocaleTextReader;
import org.support.project.knowledge.config.SystemConfig;
import org.support.project.knowledge.dao.KnowledgeFilesDao;
import org.support.project.knowledge.dao.TagsDao;
import org.support.project.knowledge.entity.DraftKnowledgesEntity;
import org.support.project.knowledge.entity.KnowledgeFilesEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.TagsEntity;
import org.support.project.knowledge.entity.TemplateMastersEntity;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.logic.TargetLogic;
import org.support.project.knowledge.logic.TemplateLogic;
import org.support.project.knowledge.logic.UploadedFileLogic;
import org.support.project.knowledge.vo.UploadFile;
import org.support.project.web.bean.LabelValue;
import org.support.project.web.dao.SystemConfigsDao;
import org.support.project.web.entity.SystemConfigsEntity;

@DI(instance = Instance.Prototype)
public class KnowledgeControlBase extends Control {
    private static final String MARKDOWN_SAMPLE = "/org/support/project/knowledge/markdown/sample_markdown.md";
    
    protected String setViewParam() {
        List<LabelValue> paramsArray = new ArrayList<>();
        paramsArray.add(new LabelValue("offset", getParamWithDefault("offset", "")));
        paramsArray.add(new LabelValue("keyword", getParamWithDefault("keyword", "")));
        paramsArray.add(new LabelValue("tag", getParamWithDefault("tag", "")));
        paramsArray.add(new LabelValue("tagNames", getParamWithDefault("tagNames", "")));
        paramsArray.add(new LabelValue("group", getParamWithDefault("group", "")));
        paramsArray.add(new LabelValue("groupNames", getParamWithDefault("groupNames", "")));
        paramsArray.add(new LabelValue("user", getParamWithDefault("user", "")));
        paramsArray.add(new LabelValue("creators", getParamWithDefault("creators", "")));
        String[] templates = getParam("template", String[].class);
        if (templates != null) {
            for (String template : templates) {
                paramsArray.add(new LabelValue("template", template));
            }
        }
        StringBuilder params = new StringBuilder();
        boolean append = false;
        for (LabelValue labelValue : paramsArray) {
            if (StringUtils.isNotEmpty(labelValue.getValue())) {
                if (!append) {
                    params.append('?');
                    append = true;
                } else {
                    params.append('&');
                }
                params.append(HtmlUtils.escapeHTML(labelValue.getLabel()))
                        .append("=")
                        .append(HtmlUtils.escapeHTML(labelValue.getValue()));
            }
        }
        setAttribute("params", params.toString());
        return params.toString();
    }
    
    protected void setAttributeForEditPage() {
        List<TagsEntity> tagitems = TagsDao.get().selectAll();
        setAttribute("tagitems", tagitems);

        List<TemplateMastersEntity> templates = TemplateLogic.get().selectAll();
        setAttribute("templates", templates);
        
        SystemConfigsEntity config = SystemConfigsDao.get().selectOnKey(SystemConfig.UPLOAD_MAX_MB_SIZE, AppConfig.get().getSystemName());
        if (config != null) {
            setAttribute("uploadMaxMBSize", config.getConfigValue());
        } else {
            setAttribute("uploadMaxMBSize", "10"); // default
        }
        
        String markdown = LocaleTextReader.get().read(MARKDOWN_SAMPLE, getLocale());
        setAttribute("markdown", markdown);
    }
    
    /**
     * ????????????????????????????????????
     * ?????????????????????Knowledge??????????????????2??????????????????????????????????????????
     * @param draft
     */
    protected void setDraftInfo(DraftKnowledgesEntity draft) {
        List<UploadFile> files = new ArrayList<UploadFile>();
        // ?????????????????????????????????????????????????????????
        List<UploadFile> draftFiles = new ArrayList<UploadFile>();
        List<KnowledgeFilesEntity> filesEntities = KnowledgeFilesDao.get().selectOnDraftId(draft.getDraftId());
        for (KnowledgeFilesEntity entity : filesEntities) {
            if (entity.getCommentNo() == null || entity.getCommentNo() == 0) {
                draftFiles.add(UploadedFileLogic.get().convUploadFile(getRequest().getContextPath(), entity));
            }
        }
        if (draft.getKnowledgeId() != null && draft.getKnowledgeId() > 0) {
            // ???????????????????????????????????????????????????Knowledge????????????????????????????????????
            KnowledgesEntity knowledge = KnowledgeLogic.get().select(draft.getKnowledgeId(), getLoginedUser());
            if (knowledge == null) {
                addMsgWarn("knowledge.draft.view.msg.not.editor");
            }
            
            // ???????????????????????????????????????????????????
            List<UploadFile> knowledgeFiles = UploadedFileLogic.get().selectOnKnowledgeIdWithoutCommentFiles(
                  draft.getKnowledgeId(), getRequest().getContextPath());
            files.addAll(knowledgeFiles);
        } else {
            draft.setKnowledgeId(null);
        }
        setAttributeOnProperty(draft);
        files.addAll(draftFiles);
        setAttribute("files", files);

        // ?????????????????????????????????
        String[] targets = draft.getAccesses().split(",");
        List<LabelValue> viewers = TargetLogic.get().selectTargets(targets);
        setAttribute("groups", viewers);
        // ???????????????
        String[] editordids = draft.getEditors().split(",");
        List<LabelValue> editors = TargetLogic.get().selectTargets(editordids);
        setAttribute("editors", editors);
        
    }
    
}
