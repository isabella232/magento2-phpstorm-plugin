/**
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */

package com.magento.idea.magento2plugin.reference.provider;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;
import com.magento.idea.magento2plugin.indexes.UIComponentIndex;
import com.magento.idea.magento2plugin.reference.xml.PolyVariantReferenceBase;
import java.util.List;
import org.jetbrains.annotations.NotNull;


public class UIComponentReferenceProvider extends PsiReferenceProvider {

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(
            @NotNull final PsiElement element,
            @NotNull final ProcessingContext context
    ) {
        final String value = StringUtil.unquoteString(element.getText());
        final List<XmlFile> targets = UIComponentIndex.getUIComponentFiles(
                element.getProject(),
                value
        );
        if (!targets.isEmpty()) {
            return new PsiReference[] {new PolyVariantReferenceBase(element, targets)};
        }
        return PsiReference.EMPTY_ARRAY;
    }
}
