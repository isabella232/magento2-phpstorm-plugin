/*
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */

package com.magento.idea.magento2plugin.actions.generation.generator.code.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.config.PhpLanguageFeature;
import com.jetbrains.php.lang.PhpCodeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.PhpDocUtil;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.PhpReturnType;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.magento.idea.magento2plugin.magento.files.Plugin;
import com.magento.idea.magento2plugin.magento.packages.MagentoPhpClass;
import com.magento.idea.magento2plugin.magento.packages.Package;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConvertPluginParamsToString {
    private ConvertPluginParamsToString() {}

    /**
     * Converts parameters to string.
     *
     * @param parameters Collection
     * @param type       PluginType
     * @param myMethod   Method
     * @return String
     */
    @SuppressWarnings({"PMD.NPathComplexity", "PMD.CyclomaticComplexity"})
    public static String execute(
            final Collection<PsiElement> parameters,
            final @NotNull Plugin.PluginType type,
            final Method myMethod
    ) {
        final StringBuilder buf = new StringBuilder();//NOPMD
        final PhpReturnType returnType = myMethod.getReturnType();
        final Iterator parametersIterator = parameters.iterator();

        Integer iterator = 0;
        while (parametersIterator.hasNext()) {
            final PhpNamedElement element = (PhpNamedElement) parametersIterator.next();
            if (iterator != 0) {
                buf.append(',');
            }

            if (element instanceof Parameter) {
                String parameterText = PhpCodeUtil.paramToString(element);
                if (parameterText.indexOf(Package.fqnSeparator, 1) > 0) {
                    final String[] fqnArray = parameterText.split("\\\\");
                    parameterText = fqnArray[fqnArray.length - 1];
                }
                buf.append(parameterText);
            } else {
                final Boolean globalType = iterator != 0;
                final String typeHint = getTypeHint(element, globalType, myMethod.getProject());
                if (typeHint != null && !typeHint.isEmpty()) {
                    buf.append(typeHint).append(' ');
                }

                String paramName = element.getName();
                if (iterator == 0) {
                    paramName = "subject";
                }
                buf.append('$').append(paramName);
            }
            if (type.equals(Plugin.PluginType.after) && iterator == 0) {
                if (returnType != null && !returnType.getText()//NOPMD
                        .equals(MagentoPhpClass.VOID_RETURN_TYPE)) {
                    buf.append(", ").append(returnType.getText()).append(" $result");
                } else {
                    buf.append(", $result");
                }
            }
            if (type.equals(Plugin.PluginType.around) && iterator == 0) {
                buf.append(", callable $proceed");
            }
            iterator++;
        }

        return buf.toString();
    }

    @Nullable
    private static String getTypeHint(
            final @NotNull PhpNamedElement element,
            final Boolean globalType,
            final Project project
    ) {
        final PhpType filedType = element.getType().global(project);
        final Set<String> typeStrings = filedType.getTypes();
        String typeString = null;
        if (typeStrings.size() == 1) { //NOPMD
            typeString = convertTypeToString(element, typeStrings, globalType);
        }

        if (typeStrings.size() == 2) { //NOPMD
            final PhpType filteredNullType = filterNullCaseInsensitive(filedType);
            if (filteredNullType.getTypes().size() == 1) { //NOPMD
                typeString = convertTypeToString(
                        element,
                        filteredNullType.getTypes(),
                        globalType
                );
                if (PhpLanguageFeature.NULLABLES.isSupported(element.getProject())) {
                    typeString = "?".concat(typeString);
                }
            }
        }

        return typeString;
    }

    @Nullable
    private static String convertTypeToString(
            final @NotNull PhpNamedElement element,
            final Set<String> typeStrings,
            final Boolean globalType
    ) {
        String simpleType = typeStrings.iterator().next();
        simpleType = StringUtil.trimStart(simpleType, "\\");
        if (!PhpType.isPrimitiveType(simpleType)
                || PhpLanguageFeature.SCALAR_TYPE_HINTS.isSupported(element.getProject())
                || MagentoPhpClass.ARRAY_TYPE.equalsIgnoreCase(simpleType)
                || Plugin.CALLABLE_PARAM.equalsIgnoreCase(simpleType)) {
            final String typeString = simpleType.endsWith("]") ? MagentoPhpClass.ARRAY_TYPE
                    : getFieldTypeString(element,
                    filterNullCaseInsensitive(element.getType()),
                    globalType
            );
            if (!typeString.isEmpty()) {
                return typeString;
            }
        }

        return null;
    }

    private static PhpType filterNullCaseInsensitive(final PhpType filedType) {
        if (filedType.getTypes().isEmpty()) {
            return PhpType.EMPTY;
        } else {
            final PhpType phpType = new PhpType();
            final Iterator iterator = filedType.getTypes().iterator();

            while (iterator.hasNext()) {
                final String type = (String) iterator.next();
                if (!type.equalsIgnoreCase("\\null")) { //NOPMD
                    phpType.add(type);
                }
            }

            return phpType;
        }
    }

    private static String getFieldTypeString(
            final PhpNamedElement element,
            final @NotNull PhpType type,
            final Boolean globalType
    ) {
        final PhpPsiElement scope = (globalType) //NOPMD
                ? PhpCodeInsightUtil.findScopeForUseOperator(element) : null;
        return PhpDocUtil.getTypePresentation(element.getProject(), type, scope);
    }
}
