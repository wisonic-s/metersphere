package io.metersphere.api.parser.jmeter.processor;

import io.metersphere.api.parser.jmeter.constants.JmeterAlias;
import io.metersphere.api.parser.jmeter.constants.JmeterProperty;
import io.metersphere.plugin.api.constants.ElementProperty;
import io.metersphere.project.api.KeyValueParam;
import io.metersphere.project.api.processor.ScriptProcessor;
import io.metersphere.project.constants.ScriptLanguageType;
import io.metersphere.project.dto.CommonScriptInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;

import java.util.List;

import static io.metersphere.api.parser.jmeter.constants.JmeterAlias.ARGUMENTS_PANEL;


/**
 * @Author: jianxing
 * @CreateTime: 2023-12-26  14:49
 */
public abstract class ScriptProcessorConverter extends MsProcessorConverter<ScriptProcessor> {

    public static final String ENV_VARIABLE_EXPRESSION = "${__metersphere_env_id}";
    public static final String MS_RUNNING_ENV_PREFIX = "MS.ENV.";

    public static void parse(TestElement testElement, ScriptProcessor scriptProcessor) {
        // 脚本安全校验
        ScriptFilter.verify(scriptProcessor.getScriptLanguage(), scriptProcessor.getName(), scriptProcessor.getScript());

        testElement.setEnabled(scriptProcessor.getEnable());
        String name = StringUtils.isEmpty(scriptProcessor.getName()) ? scriptProcessor.getClass().getSimpleName() : scriptProcessor.getName();
        testElement.setName(name);

        // todo 替换环境变量
//        String evnId = scriptProcessor.getEnvironmentId();
//        if (StringUtils.isNotEmpty(scriptProcessor.getScript())) {
//            scriptProcessor.setScript(StringUtils.replace(scriptProcessor.getScript(), ENV_VARIABLE_EXPRESSION, "\"" + MS_RUNNING_ENV_PREFIX + evnId + ".\""));
//        }

        // python 和 js cache 打开
        boolean cacheKey = StringUtils.equalsAny(scriptProcessor.getScriptLanguage(), ScriptLanguageType.PYTHON.name(), ScriptLanguageType.JAVASCRIPT.name());
        testElement.setProperty(JmeterProperty.CACHE_KEY, cacheKey);
        testElement.setProperty(TestElement.TEST_CLASS, testElement.getClass().getSimpleName());
        testElement.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass(JmeterAlias.TEST_BEAN_GUI));
        testElement.setProperty(ElementProperty.PROJECT_ID.name(), scriptProcessor.getProjectId());
        String scriptLanguage = scriptProcessor.getScriptLanguage();
        String script = scriptProcessor.getScript();
        if (scriptProcessor.isEnableCommonScript()) {
            scriptLanguage = scriptProcessor.getCommonScriptInfo().getScriptLanguage();
            script = scriptProcessor.getCommonScriptInfo().getScript();
        }
        if (scriptLanguage == null || StringUtils.equalsIgnoreCase(scriptLanguage, ScriptLanguageType.BEANSHELL_JSR233.name())) {
            scriptLanguage = ScriptLanguageType.BEANSHELL.name();
        }
        testElement.setProperty(JmeterProperty.SCRIPT, script);
        testElement.setProperty(JmeterProperty.SCRIPT_LANGUAGE, scriptLanguage.toLowerCase());
    }

    public static Arguments getScriptArguments(ScriptProcessor scriptProcessor) {
        if (scriptProcessor == null || !scriptProcessor.isEnableCommonScript() || !scriptProcessor.isValid()) {
            return null;
        }

        CommonScriptInfo commonScriptInfo = scriptProcessor.getCommonScriptInfo();
        if (CollectionUtils.isEmpty(commonScriptInfo.getParams())) {
            return null;
        }

        List<KeyValueParam> params = commonScriptInfo.getParams()
                .stream()
                .filter(KeyValueParam::isValid)
                .toList();

        if (CollectionUtils.isEmpty(commonScriptInfo.getParams())) {
            return null;
        }

        Arguments arguments = new Arguments();
        arguments.setEnabled(true);
        arguments.setName(scriptProcessor.getName() + "_Arguments");
        arguments.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
        arguments.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass(ARGUMENTS_PANEL));
        for (KeyValueParam param : params) {
            arguments.addArgument(param.getKey(), param.getValue(), "=");
        }
        return arguments;
    }

    public static boolean isJSR233(ScriptProcessor scriptProcessor) {
        return !StringUtils.equals(scriptProcessor.getScriptLanguage(), ScriptLanguageType.BEANSHELL.name());
    }
}