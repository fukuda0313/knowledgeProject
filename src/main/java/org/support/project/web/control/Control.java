package org.support.project.web.control;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.support.project.common.bean.ValidateError;
import org.support.project.common.config.CommonBaseParameter;
import org.support.project.common.config.Resources;
import org.support.project.common.exception.SystemException;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.PropertyUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.web.bean.DownloadInfo;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.MessageResult;
import org.support.project.web.boundary.Boundary;
import org.support.project.web.boundary.DownloadBoundary;
import org.support.project.web.boundary.ForwardBoundary;
import org.support.project.web.boundary.JsonBoundary;
import org.support.project.web.boundary.RedirectBoundary;
import org.support.project.web.boundary.SendMessageBoundary;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.common.HttpUtil;
import org.support.project.web.common.InvokeSearch;
import org.support.project.web.common.InvokeTarget;
import org.support.project.web.config.CommonWebParameter;
import org.support.project.web.config.HttpMethod;
import org.support.project.web.config.MessageStatus;
import org.support.project.web.control.service.Get;
import org.support.project.web.exception.InvalidParamException;
import org.support.project.web.logic.impl.DefaultAuthenticationLogicImpl;

import net.arnx.jsonic.JSON;
import net.arnx.jsonic.JSONException;

/**
 * Control??????????????????
 * 
 * @author Koda
 */
@DI(instance = Instance.Prototype)
public abstract class Control {
    /** ?????? */
    private static Log log = LogFactory.getLog(Control.class);

    /** HttpServletRequest */
    private HttpServletRequest request;
    /** HttpServletResponse */
    private HttpServletResponse response;
    /** ???????????????????????????????????????????????????????????? */
    private String path;
    /** ??????????????????????????????????????????????????????????????????????????????????????? */
    private String pathInfo;

    /** Control???????????????????????????????????? */
    private InvokeTarget invokeTarget = null;
    /** ?????????????????????????????????????????? */
    private String forwardDir = null;

    /** ???????????? */
    protected Resources resources = Resources.getInstance(CommonBaseParameter.APP_RESOURCE);
    /** View?????????????????? */
    public static final String BASE_PATH = "/WEB-INF/views";
    /** HTML???????????????????????????????????????????????? */
    private boolean sendEscapeHtml = true;

    /**
     * ??????????????????????????????
     * 
     * @return Boundary
     */
    @Get
    public Boundary index() {
        log.trace("index");
        return forward("index.jsp");
    }

    /**
     * ????????????
     * 
     * @return Boundary
     */
    @Get
    public Boundary lang() {
        log.trace("lang");
        return forward("index.jsp");
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????????????? ??????????????????????????????????????????????????????
     * 
     * @param method method
     * @param path path
     * @return Boundary
     */
    protected Boundary devolution(HttpMethod method, String path) {
        return devolution(method, path, getPathInfo());
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????????????? ??????????????????????????????????????????????????????
     * 
     * @param method method
     * @param path path
     * @param pathinfo pathinfo
     * @return Boundary
     */
    protected Boundary devolution(HttpMethod method, String path, String pathinfo) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        InvokeSearch invokeSearch = Container.getComp(InvokeSearch.class);
        InvokeTarget invokeTarget = invokeSearch.getController(method, path, pathinfo);
        if (invokeTarget != null && Control.class.isAssignableFrom(invokeTarget.getTargetClass())) {
            Control control = (Control) invokeTarget.getTarget();
            copy(control);
            control.setPath(path);
            control.setInvokeTarget(invokeTarget);
            control.setPathInfo(pathinfo);
            return (Boundary) invokeTarget.invoke();
        }
        throw new SystemException("can not devolution path :" + path);
    }

    /**
     * ????????????Control???????????????????????????????????????????????????
     * 
     * @param control Control
     */
    protected void copy(Control control) {
        control.setPathInfo(pathInfo);
        control.setRequest(request);
        control.setResponse(response);
    }

    /**
     * ?????????????????????????????????????????????
     * 
     * @return directory path
     */
    protected String getForwardDir() {
        if (forwardDir != null) {
            return forwardDir;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(BASE_PATH);
        builder.append("/");
        // String className = ClassUtils.getShortClassName(this.getClass());
        String className = this.getClass().getSimpleName();
        if (className.indexOf("_$$_") != -1) {
            className = className.substring(0, className.indexOf("_$$_"));
        }

        if (invokeTarget != null) {
            String subPackageName = invokeTarget.getSubPackageName();
            if (StringUtils.isNotEmpty(subPackageName)) {
                if (subPackageName.indexOf("\\.") != -1) {
                    String[] packages = StringUtils.split(subPackageName, ".");
                    for (int i = packages.length - 1; i <= 0; i--) {
                        String string = packages[i];
                        builder.append(string).append("/");
                    }
                } else {
                    builder.append(subPackageName).append("/");
                }
            }

            if (className.endsWith(invokeTarget.getClassSuffix())) {
                className = className.substring(0, className.length() - invokeTarget.getClassSuffix().length());
            }
        }
        builder.append(className.toLowerCase()).append("/");

        forwardDir = builder.toString();
        return forwardDir;
    }

    /**
     * ?????????????????????????????????
     * 
     * @param path path
     * @return ForwardBoundary
     */
    protected ForwardBoundary forward(String path) {
        String forwardPath;
        if (path.startsWith("/")) {
            StringBuilder builder = new StringBuilder();
            builder.append(BASE_PATH);
            builder.append(path);
            forwardPath = builder.toString();
        } else {
            forwardPath = getForwardDir().concat(path);
            if (log.isTraceEnabled()) {
                log.trace("forward : " + forwardPath);
            }
        }

        ForwardBoundary boundary = new ForwardBoundary(forwardPath);
        boundary.setRequest(request);
        boundary.setResponse(response);
        return boundary;
    }

    /**
     * ????????????????????????????????????
     * 
     * @param path path
     * @return RedirectBoundary
     */
    protected RedirectBoundary redirect(String path) {
        RedirectBoundary boundary = new RedirectBoundary(path);
        boundary.setRequest(request);
        boundary.setResponse(response);
        return boundary;
    }

    /**
     * JSON???????????????????????????
     * 
     * @param json send object to convert json
     * @return JsonBoundary
     */
    protected JsonBoundary send(Object json) {
        if (json instanceof MessageResult) {
            MessageResult messageResult = (MessageResult) json;
            if (messageResult.getStatus() > MessageStatus.Success.getValue()) {
                if (messageResult.getCode() != null && messageResult.getCode() > HttpStatus.SC_200_OK) {
                    return send(messageResult.getCode(), messageResult);
                } else if (messageResult.getCode() == null) {
                    return send(HttpStatus.SC_400_BAD_REQUEST, messageResult);
                }
            }
        }
        JsonBoundary boundary = new JsonBoundary(json);
        boundary.setRequest(request);
        boundary.setResponse(response);
        boundary.setSendEscapeHtml(sendEscapeHtml);
        return boundary;
    }

    /**
     * JSON???????????????????????????
     * 
     * @param json json
     * @param status status
     * @return JsonBoundary
     */
    protected JsonBoundary send(int status, Object json) {
        if (json instanceof MessageResult) {
            MessageResult messageResult = (MessageResult) json;
            messageResult.setCode(status);
        }
        JsonBoundary boundary = new JsonBoundary(json);
        response.setStatus(status);
        boundary.setRequest(request);
        boundary.setResponse(response);
        boundary.setSendEscapeHtml(sendEscapeHtml);
        return boundary;
    }

    /**
     * ?????????????????????
     * 
     * @param messageStatus messageStatus
     * @param httpStatus httpstatus
     * @param result result
     * @param messageKey messageKey
     * @param params params
     * @return JsonBoundary
     */
    protected JsonBoundary sendMsg(MessageStatus messageStatus, int httpStatus, String result, String messageKey, String... params) {
        Resources resources = Resources.getInstance(HttpUtil.getLocale(getRequest()));
        String msg = resources.getResource(messageKey, params);
        MessageResult messageResult = new MessageResult();
        messageResult.setMessage(msg);
        messageResult.setStatus(messageStatus.getValue());
        messageResult.setCode(httpStatus);
        messageResult.setResult(result);
        return send(messageResult);
    }

    /**
     * ??????????????????
     * 
     * @param errors errors
     * @return JsonBoundary
     */
    protected JsonBoundary sendValidateError(List<ValidateError> errors) {
        MessageResult messageResult = new MessageResult();
        messageResult.setMessage("Bad Request");
        messageResult.setResult("Bad Request");
        messageResult.setCode(HttpStatus.SC_400_BAD_REQUEST);

        int status = MessageStatus.Warning.getValue();
        for (ValidateError validateError : errors) {
            String msg = validateError.getMsg(HttpUtil.getLocale(getRequest()));
            if (validateError.getLevel() == MessageStatus.Error.getValue()) {
                status = MessageStatus.Error.getValue();
            }
            MessageResult child = new MessageResult();
            child.setMessage(msg);
            messageResult.getChildren().add(child);
        }
        messageResult.setStatus(status);
        return send(messageResult);
    }

    /**
     * ??????????????????
     * 
     * @param status status code
     * @param msg msg
     * @return SendMessageBoundary
     */
    protected SendMessageBoundary sendError(int status, String msg) {
        SendMessageBoundary boundary = new SendMessageBoundary();
        boundary.setRequest(request);
        boundary.setResponse(response);

        boundary.setStatus(status);
        boundary.setMsg(msg);
        return boundary;
    }

    /**
     * ??????????????????
     * @param down Download information
     * @return Boundary
     */
    protected Boundary download(DownloadInfo down) {
        return download(down.getFileName(), down.getInputStream(), down.getSize(), down.getContentType());
    }
    
    /**
     * ??????????????????
     * 
     * @param fileName fileName
     * @param inputStream inputStream
     * @param size size
     * @return Boundary
     */
    protected Boundary download(String fileName, InputStream inputStream, long size) {
        DownloadBoundary boundary = new DownloadBoundary(fileName, inputStream, size);
        boundary.setRequest(request);
        boundary.setResponse(response);
        return boundary;
    }

    /**
     * ??????????????????
     * 
     * @param fileName fileName
     * @param inputStream inputStream
     * @param size size
     * @param contentType contentType
     * @return Boundary
     */
    protected Boundary download(String fileName, InputStream inputStream, long size, String contentType) {
        DownloadBoundary boundary = new DownloadBoundary(fileName, inputStream, size, contentType);
        boundary.setRequest(request);
        boundary.setResponse(response);
        return boundary;
    }

    /**
     * ??????????????????Attribute?????????(String???)
     * 
     * @param name name
     * @return string
     */
    protected String getParameter(String name) {
        Object val = request.getAttribute(name);
        if (val == null) {
            return "";
        }
        if (val instanceof String) {
            return (String) val;
        }
        return val.toString();
    }

    /**
     * ??????????????????Attribute????????????
     * 
     * @param key key
     * @param value value
     */
    protected void setAttribute(String key, Object value) {
        request.setAttribute(key, value);
    }

    /**
     * ??????????????????Attribute?????????
     * 
     * @param name name
     * @return value
     */
    protected Object getAttribute(String name) {
        return request.getAttribute(name);
    }

    /**
     * ??????????????????Attribute?????????
     * 
     * @param name name
     * @param defaultValue defaultValue
     * @return value
     */
    protected String getAttribute(String name, String defaultValue) {
        Object obj = request.getAttribute(name);
        if (obj == null) {
            return defaultValue;
        }
        return obj.toString();
    }

    /**
     * ???????????????????????????
     * 
     * @param value????????????????????????setAttribute?????????
     */
    protected void setAttributeOnProperty(Object value) {
        List<String> props = PropertyUtil.getPropertyNames(value);
        for (String key : props) {
            request.setAttribute(key, PropertyUtil.getPropertyValue(value, key));
        }
    }

    /**
     * ?????????????????????JSON????????????????????????????????????????????????
     * 
     * @param type type
     * @param <T> type
     * @return value object
     * @throws JSONException JSONException
     * @throws IOException IOException
     */
    protected <T> T getJsonObject(Class<? extends T> type) throws JSONException, IOException {
        HttpServletRequest request = getRequest();
        T object = JSON.decode(request.getInputStream(), type);
        return object;
    }

    /**
     * ??????????????????????????????
     * 
     * @return path infos
     */
    protected String[] getPathInfos() {
        if (StringUtils.isEmpty(pathInfo)) {
            return null;
        }
        if (pathInfo.indexOf("/") != -1) {
            String[] infos = pathInfo.split("/");
            List<String> list = new ArrayList<>();
            for (String string : infos) {
                if (StringUtils.isNotEmpty(string.trim())) {
                    list.add(string.trim());
                }
            }
            return list.toArray(new String[0]);
        } else {
            String[] infos = new String[1];
            infos[0] = pathInfo.trim();
            return infos;
        }
    }

    /**
     * ??????????????????????????????
     * 
     * @return LoginedUser
     */
    public LoginedUser getLoginedUser() {
        return (LoginedUser) request.getSession().getAttribute(CommonWebParameter.LOGIN_USER_INFO_SESSION_KEY);
    }

    /**
     * ??????????????????????????????????????????????????????
     */
    public void updateLoginInfo() {
        DefaultAuthenticationLogicImpl authenticationLogic = Container.getComp(DefaultAuthenticationLogicImpl.class);
        LoginedUser loginedUser = getLoginedUser();
        if (loginedUser != null) {
            authenticationLogic.clearSession(request);
            authenticationLogic.setSession(loginedUser.getLoginUser().getUserKey(), request, response);
        }
    }

    /**
     * ????????????????????????ID?????????
     * 
     * @return userid
     */
    public Integer getLoginUserId() {
        LoginedUser loginedUser = getLoginedUser();
        if (loginedUser == null) {
            return Integer.MIN_VALUE;
        }
        return loginedUser.getLoginUser().getUserId();
    }

    /**
     * request ?????????
     * 
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * request ????????????
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * response ?????????
     * 
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * response ????????????
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * invokeTarget ?????????
     * 
     * @return invokeTarget
     */
    public InvokeTarget getInvokeTarget() {
        return invokeTarget;
    }

    /**
     * invokeTarget ????????????
     * @param invokeTarget invokeTarget
     */
    public void setInvokeTarget(InvokeTarget invokeTarget) {
        this.invokeTarget = invokeTarget;
    }

    /**
     * path ?????????
     * 
     * @return path
     */
    public String getPath() {
        return path;
    }

    /**
     * path ????????????
     * @param path path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * pathInfo ?????????
     * 
     * @return pathinfo
     */
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * pathInfo ????????????
     * 
     * @param pathInfo pathInfo
     */
    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    /**
     * PathInfo??????????????????????????????????????????
     * @param defaultStr defaultStr
     * @return string
     */
    protected String getPathString(String defaultStr) {
        String[] pathInfos = getPathInfos();
        if (pathInfos == null || pathInfos.length == 0) {
            return defaultStr;
        }
        return pathInfos[0];
    }

    
    /**
     * PathInfo??????????????????????????????????????????
     * @return string
     * @throws InvalidParamException InvalidParamException
     */
    protected String getPathString() throws InvalidParamException {
        String[] pathInfos = getPathInfos();
        if (pathInfos == null || pathInfos.length == 0 || StringUtils.isEmpty(pathInfos[0])) {
            MessageResult result = new MessageResult();
            result.setStatus(MessageStatus.Warning.getValue());
            result.setMessage(resources.getResource("errors.required", "id"));
            result.setCode(HttpStatus.SC_400_BAD_REQUEST);
            throw new InvalidParamException(result);
        }
        return pathInfos[0];
    }

    /**
     * PathInfo??????????????????????????????Integer?????????????????????
     * 
     * @param defaultNum defaultNum
     * @return int value
     * @throws InvalidParamException InvalidParamException
     */
    protected Integer getPathInteger(Integer defaultNum) throws InvalidParamException {
        String[] pathInfos = getPathInfos();
        if (pathInfos == null || pathInfos.length == 0 || StringUtils.isEmpty(pathInfos[0])) {
            if (defaultNum != null) {
                return defaultNum;
            }
            MessageResult result = new MessageResult();
            result.setStatus(MessageStatus.Warning.getValue());
            result.setMessage(resources.getResource("errors.required", "param"));
            result.setCode(HttpStatus.SC_400_BAD_REQUEST);
            throw new InvalidParamException(result);
        }
        String param = pathInfos[0];
        if (!StringUtils.isInteger(param)) {
            MessageResult result = new MessageResult();
            result.setStatus(MessageStatus.Warning.getValue());
            result.setMessage(resources.getResource("errors.invalid", "param"));
            result.setCode(HttpStatus.SC_400_BAD_REQUEST);
            throw new InvalidParamException(result);
        }
        return Integer.parseInt(param);
    }

    /**
     * PathInfo??????????????????????????????Integer?????????????????????
     * 
     * @return int value
     * @throws InvalidParamException InvalidParamException
     */
    protected Integer getPathInteger() throws InvalidParamException {
        return getPathInteger(null);
    }

    /**
     * PathInfo??????????????????????????????Long?????????????????????
     * 
     * @param defaultNum defaultNum
     * @return long value
     * @throws InvalidParamException InvalidParamException
     */
    protected Long getPathLong(Long defaultNum) throws InvalidParamException {
        String[] pathInfos = getPathInfos();
        if (pathInfos == null || pathInfos.length == 0 || StringUtils.isEmpty(pathInfos[0])) {
            if (defaultNum != null) {
                return defaultNum;
            }
            MessageResult result = new MessageResult();
            result.setStatus(MessageStatus.Warning.getValue());
            result.setMessage(resources.getResource("errors.required", "param"));
            result.setCode(HttpStatus.SC_400_BAD_REQUEST);
            throw new InvalidParamException(result);
        }
        String param = pathInfos[0];
        if (!StringUtils.isLong(param)) {
            MessageResult result = new MessageResult();
            result.setStatus(MessageStatus.Warning.getValue());
            result.setMessage(resources.getResource("errors.invalid", "param"));
            result.setCode(HttpStatus.SC_400_BAD_REQUEST);
            throw new InvalidParamException(result);
        }
        return Long.parseLong(param);
    }

    /**
     * PathInfo??????????????????????????????Integer?????????????????????
     * 
     * @return value
     * @throws InvalidParamException InvalidParamException
     */
    protected Long getPathLong() throws InvalidParamException {
        return getPathLong(null);
    }

    /**
     * ?????????????????????
     * 
     * @param paramName paramName
     * @return value
     */
    protected String getParam(String paramName) {
        return getParamWithDefault(paramName, "");
    }

    /**
     * ????????????????????? ?????????????????????????????????????????????????????????
     * 
     * @param paramName paramName
     * @param defaultval defaultval
     * @return value
     */
    protected String getParamWithDefault(String paramName, String defaultval) {
        try {
            String param = HttpUtil.getParameter(request, paramName, String.class);
            if (param == null) {
                return defaultval;
            }
            return param;
        } catch (InvalidParamException e) {
            throw new SystemException(e);
        }
    }

    /**
     * ????????????????????????
     * 
     * @param paramName paramName
     * @param type type
     * @param <T> type
     * @return value
     */
    protected <T> T getParam(String paramName, Class<? extends T> type) {
        try {
            return HttpUtil.getParameter(request, paramName, type);
        } catch (InvalidParamException e) {
            throw new SystemException(e);
        }
    }

    /**
     * ????????????????????????
     * 
     * @param type type
     * @param <T> type
     * @return value
     */
    protected <T> T getParams(Class<? extends T> type) {
        try {
            return HttpUtil.parseRequest(request, type);
        } catch (InstantiationException | IllegalAccessException | JSONException | IOException | InvalidParamException e) {
            throw new SystemException(e);
        }
    }

    /**
     * ????????????????????????????????????
     * 
     * @return value map
     */
    protected Map<String, String> getParams() {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> enumeration = request.getParameterNames();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            String value = request.getParameter(key);
            map.put(key, value);
        }
        return map;
    }
    
    /**
     * ????????????????????????
     * @param param ??????????????????
     * @param defaultVal ???????????????
     * @param maxVal ?????????
     * @return ??????
     */
    protected int getParamInt(String param, int defaultVal, int maxVal) {
        int num = defaultVal;
        String str = getParam(param);
        if (StringUtils.isInteger(str)) {
            num = Integer.parseInt(str);
        }
        if(num > maxVal) {
            num = maxVal;
        }
        return num;
    }
    
    
    /**
     * @return the sendEscapeHtml
     */
    public boolean isSendEscapeHtml() {
        return sendEscapeHtml;
    }

    /**
     * @param sendEscapeHtml the sendEscapeHtml to set
     */
    public void setSendEscapeHtml(boolean sendEscapeHtml) {
        this.sendEscapeHtml = sendEscapeHtml;
    }

    /**
     * ????????????????????????????????????????????????????????????
     * 
     * @param type type
     * @param <T> type
     * @return value
     * @throws InstantiationException InstantiationException
     * @throws IllegalAccessException IllegalAccessException
     * @throws JSONException JSONException
     * @throws IOException IOException
     * @throws InvalidParamException InvalidParamException
     */
    public <T> T getParamOnProperty(final Class<? extends T> type)
            throws InstantiationException, IllegalAccessException, JSONException, IOException, InvalidParamException {
        return HttpUtil.parseRequest(request, type);
    }

    /**
     * ??????????????????Cookie????????????
     * 
     * @param key key
     * @return value
     */
    protected String getCookie(String key) {
        return HttpUtil.getCookie(getRequest(), key);
    }

    /**
     * ?????????Cookie????????????
     * 
     * @param key key
     * @param value value
     */
    protected void setCookie(String key, String value) {
        HttpUtil.setCookie(getRequest(), getResponse(), key, value);
    }

    /**
     * Locale?????????
     * 
     * @return Locale
     */
    protected Locale getLocale() {
        return HttpUtil.getLocale(request);
    }

    /**
     * ????????????????????????????????????
     * @param key key
     * @return string
     */
    protected String getResource(String key) {
        Resources resources = Resources.getInstance(HttpUtil.getLocale(getRequest()));
        return resources.getResource(key);
    }
    /**
     * ????????????????????????????????????
     * @param key key
     * @param params params
     * @return string
     */
    protected String getResource(String key, String... params) {
        Resources resources = Resources.getInstance(HttpUtil.getLocale(getRequest()));
        return resources.getResource(key, params);
    }

}
