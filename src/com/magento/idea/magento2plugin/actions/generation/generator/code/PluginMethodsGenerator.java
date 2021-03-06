/*
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */

package com.magento.idea.magento2plugin.actions.generation.generator.code;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.config.PhpLanguageFeature;
import com.jetbrains.php.lang.PhpCodeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.PhpReturnType;
import com.magento.idea.magento2plugin.actions.generation.data.code.PluginMethodData;
import com.magento.idea.magento2plugin.actions.generation.generator.code.util.ConvertPluginParamsToPhpDocStringUtil;
import com.magento.idea.magento2plugin.actions.generation.generator.code.util.ConvertPluginParamsToString;
import com.magento.idea.magento2plugin.magento.files.Plugin;
import com.magento.idea.magento2plugin.magento.packages.MagentoPhpClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginMethodsGenerator {
    public static String originalTargetKey = "original.target";
    @NotNull
    private final PhpClass pluginClass;
    @NotNull
    private final Method myMethod;
    @NotNull
    private final PhpClass myTargetClass;

    /**
     * Constructor.
     *
     * @param pluginClass PhpClass
     * @param method Method
     * @param targetClassKey Key
     */
    public PluginMethodsGenerator(
            final @NotNull PhpClass pluginClass,
            final @NotNull Method method,
            final Key<Object> targetClassKey
    ) {
        super();
        this.pluginClass = pluginClass;
        this.myMethod = method;
        this.myTargetClass = (PhpClass)Objects
            .requireNonNull(method.getUserData(targetClassKey));
    }

    /**
     * Create Plugin Methods DTO's.
     *
     * @param type PluginType
     * @return PluginMethodData[]
     */
    @NotNull
    public PluginMethodData[] createPluginMethods(final @NotNull Plugin.PluginType type) {
        final List<PluginMethodData> pluginMethods = new ArrayList();
        final PhpClass currentClass = this.pluginClass;
        final String templateName = Plugin.getMethodTemplateByPluginType(type);
        final Properties attributes = this.getAccessMethodAttributes(
                PhpCodeInsightUtil.findScopeForUseOperator(this.myMethod),
                type
        );
        final String methodTemplate = PhpCodeUtil.getCodeTemplate(
                templateName,
                attributes,
                this.pluginClass.getProject()
        );
        final PhpClass dummyClass = PhpCodeUtil.createClassFromMethodTemplate(
                currentClass,
                currentClass.getProject(),
                methodTemplate
        );
        if (dummyClass != null) {
            PhpDocComment currDocComment = null;

            for (PsiElement child = dummyClass.getFirstChild(); child != null;
                    child = child.getNextSibling()) {
                if (child instanceof PhpDocComment) {
                    currDocComment = (PhpDocComment)child;
                } else if (child instanceof Method) {
                    pluginMethods.add(
                            new PluginMethodData(myMethod, currDocComment, (Method)child)//NOPMD
                    );
                }
            }
        }

        return pluginMethods.toArray(new PluginMethodData[0]);
    }

    private Properties getAccessMethodAttributes(
            final @Nullable PhpPsiElement scopeForUseOperator,
            final @NotNull Plugin.PluginType type
    ) {
        final Properties attributes = new Properties();
        final String typeHint = this.fillAttributes(scopeForUseOperator, attributes, type);
        this.addTypeHintsAndReturnType(attributes, typeHint);
        return attributes;
    }

    @NotNull
    private String fillAttributes(
            final @Nullable PhpPsiElement scopeForUseOperator,
            final Properties attributes,
            final @NotNull Plugin.PluginType type
    ) {
        final String fieldName = this.myMethod.getName();
        final String methodSuffix = fieldName.substring(0, 1).toUpperCase(Locale.getDefault())
                + fieldName.substring(1);
        final String pluginMethodName = type.toString().concat(methodSuffix);

        attributes.setProperty("NAME", pluginMethodName);
        final PhpReturnType targetMethodReturnType = myMethod.getReturnType();
        String typeHint = "";
        if (targetMethodReturnType != null) {
            typeHint = targetMethodReturnType.getText();
        }
        final Collection<PsiElement> parameters = new ArrayList();
        parameters.add(myTargetClass);
        parameters.addAll(Arrays.asList(myMethod.getParameters()));
        attributes.setProperty("PARAM_DOC", ConvertPluginParamsToPhpDocStringUtil.execute(
                parameters,
                type,
                scopeForUseOperator.getProject(),
                myMethod)
        );
        attributes.setProperty("PARAM_LIST", ConvertPluginParamsToString
                .execute(parameters, type, myMethod));
        final String returnVariables = this.getReturnVariables(type);
        if (returnVariables != null) {
            attributes.setProperty("RETURN_VARIABLES", returnVariables);
        }

        return typeHint;
    }

    private void addTypeHintsAndReturnType(final Properties attributes, final String typeHint) {
        final Project project = this.pluginClass.getProject();
        if (PhpLanguageFeature.SCALAR_TYPE_HINTS.isSupported(project)
                && isDocTypeConvertable(typeHint)) {
            attributes.setProperty("SCALAR_TYPE_HINT", convertDocTypeToHint(project, typeHint));
        }

        final boolean hasFeatureVoid = PhpLanguageFeature.RETURN_VOID.isSupported(project);
        if (hasFeatureVoid) {
            attributes.setProperty("VOID_RETURN_TYPE", MagentoPhpClass.VOID_RETURN_TYPE);
        }

        if (PhpLanguageFeature.RETURN_TYPES.isSupported(project)
                && isDocTypeConvertable(typeHint)) {
            attributes.setProperty("RETURN_TYPE", convertDocTypeToHint(project, typeHint));
        }

    }

    private static boolean isDocTypeConvertable(final String typeHint) {
        return !typeHint.equalsIgnoreCase(MagentoPhpClass.MIXED_RETURN_TYPE)
            && !typeHint.equalsIgnoreCase(MagentoPhpClass.STATIC_MODIFIER)
            && !typeHint.equalsIgnoreCase(MagentoPhpClass.PHP_TRUE)
            && !typeHint.equalsIgnoreCase(MagentoPhpClass.PHP_FALSE)
            && !typeHint.equalsIgnoreCase(MagentoPhpClass.PHP_NULL)
            && (!typeHint.contains(MagentoPhpClass.DOC_TYPE_SEPARATOR)
            || typeWithNull(typeHint));
    }

    private static boolean typeWithNull(final String typeHint) {
        return typeHint.split(Pattern.quote(MagentoPhpClass.DOC_TYPE_SEPARATOR)).length == 2
                && StringUtil.toUpperCase(typeHint).contains("NULL");
    }

    private static String convertNullableType(final Project project, final String typeHint) {
        final String[] split = typeHint.split(Pattern.quote(MagentoPhpClass.DOC_TYPE_SEPARATOR));
        final boolean hasNullableTypeFeature = PhpLanguageFeature.NULLABLES.isSupported(project);
        return split[0].equalsIgnoreCase(MagentoPhpClass.PHP_NULL)
                ? (hasNullableTypeFeature ? "?" : "") + split[1]
                : (hasNullableTypeFeature ? "?" : "") + split[0];
    }

    @NotNull
    private static String convertDocTypeToHint(final Project project, final String typeHint) {
        String hint = typeHint.contains("[]") ? "array" : typeHint;
        hint = hint.contains("boolean") ? "bool" : hint;
        if (typeWithNull(typeHint)) {
            hint = convertNullableType(project, hint);
        }

        return hint;
    }

    protected String getReturnVariables(final @NotNull Plugin.PluginType type) {
        final StringBuilder buf = new StringBuilder();
        if (type.equals(Plugin.PluginType.after)) {
            return buf.append("$result").toString();
        }
        final Parameter[] parameters = myMethod.getParameters();
        if (parameters.length == 0) {
            return null;
        }
        boolean isFirst = true;

        for (final Parameter parameter: parameters) {
            if (isFirst) {
                isFirst = false;
            } else {
                buf.append(", ");
            }

            final String paramName = parameter.getName();
            buf.append('$').append(paramName);
        }

        return buf.toString();
    }
}
