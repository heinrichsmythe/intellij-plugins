// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.entities.source;

import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator;
import com.intellij.lang.javascript.psi.impl.JSPropertyImpl;
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement;
import com.intellij.lang.javascript.psi.stubs.JSPropertyStub;
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.AstLoadingFilter;
import one.util.streamex.StreamEx;
import org.angular2.Angular2InjectionUtils;
import org.angular2.entities.Angular2Component;
import org.angularjs.codeInsight.refs.AngularJSTemplateReferencesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.util.CachedValueProvider.Result.create;
import static org.angular2.Angular2DecoratorUtil.*;

public class Angular2SourceComponent extends Angular2SourceDirective implements Angular2Component {

  public Angular2SourceComponent(@NotNull ES6Decorator decorator, @NotNull JSImplicitElement implicitElement) {
    super(decorator, implicitElement);
  }

  @Nullable
  @Override
  public PsiFile getTemplateFile() {
    return getCachedValue(() -> create(
      findAngularComponentTemplate(), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, getDecorator()));
  }

  @NotNull
  @Override
  public List<PsiFile> getCssFiles() {
    return getCachedValue(() -> create(findCssFiles(), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, getDecorator()));
  }

  @Override
  public boolean isStructuralDirective() {
    return false;
  }

  @Override
  public boolean isRegularDirective() {
    return true;
  }

  @Nullable
  private JSProperty getDecoratorProperty(@NotNull String name) {
    return getProperty(getDecorator(), name);
  }

  @Nullable
  private PsiFile findAngularComponentTemplate() {
    PsiFile file = getReferencedFile(getDecoratorProperty(TEMPLATE_URL_PROP), true);
    return file != null ? file
                        : getReferencedFile(getDecoratorProperty(TEMPLATE_PROP), false);
  }

  private List<PsiFile> findCssFiles() {
    return findCssFiles(getDecoratorProperty(STYLE_URLS_PROP), true)
      .append(findCssFiles(getDecoratorProperty(STYLES_PROP), false))
      .toList();
  }

  private static StreamEx<PsiFile> findCssFiles(@Nullable JSProperty property, boolean directRefs) {
    if (property == null) {
      return StreamEx.empty();
    }
    // TODO need stubbed references
    if (directRefs) { // styles property can contain references to CSS files imported through import statements
      JSPropertyStub stub = ((JSPropertyImpl)property).getStub();
      if (stub != null) {
        return StreamEx.of(stub.getChildrenStubs())
          .map(StubElement::getPsi)
          .select(JSExpression.class)
          .map(expr -> getReferencedFileFromStub(expr, directRefs));
      }
    }
    return AstLoadingFilter.forceAllowTreeLoading(property.getContainingFile(), () ->
      StreamEx.ofNullable(property.getValue())
        .select(JSArrayLiteralExpression.class)
        .flatArray(JSArrayLiteralExpression::getExpressions)
        .map(expr -> getReferencedFileFromPsi(expr, directRefs)));
  }

  @StubSafe
  private static PsiFile getReferencedFile(@Nullable JSProperty property, boolean directRefs) {
    if (property == null) {
      return null;
    }
    // TODO need stubbed references
    if (directRefs) { // template property can contain references to HTML files imported through import statements
      JSPropertyStub stub = ((JSPropertyImpl)property).getStub();
      if (stub != null) {
        return getReferencedFileFromStub(StreamEx.of(stub.getChildrenStubs())
                                           .map(StubElement::getPsi)
                                           .select(JSExpression.class)
                                           .findFirst()
                                           .orElse(null),
                                         directRefs);
      }
    }
    return AstLoadingFilter.forceAllowTreeLoading(property.getContainingFile(),
                                                  () -> getReferencedFileFromPsi(property.getValue(), directRefs));
  }

  @StubSafe
  private static PsiFile getReferencedFileFromStub(@Nullable JSExpression stubbedExpression, boolean directRefs) {
    if (!directRefs
        && stubbedExpression instanceof JSCallExpression
        && ((JSCallExpression)stubbedExpression).isRequireCall()) {
      stubbedExpression = JSStubBasedPsiTreeUtil.findRequireCallArgument((JSCallExpression)stubbedExpression);
    }

    String url = null;
    if (stubbedExpression instanceof JSLiteralExpression) {
      url = ((JSLiteralExpression)stubbedExpression).getSignificantValue();
    }

    if (url != null) {
      PsiElement fakeUrlElement = new FakeStringLiteral(stubbedExpression, url);
      for (FileReference ref : new AngularJSTemplateReferencesProvider.Angular2SoftFileReferenceSet(fakeUrlElement).getAllReferences()) {
        PsiElement el = ref.resolve();
        if (el instanceof PsiFile) {
          return (PsiFile)el;
        }
      }
    }
    return null;
  }

  @StubUnsafe
  private static PsiFile getReferencedFileFromPsi(@Nullable JSExpression expression, boolean directRefs) {
    if (expression != null) {
      PsiFile file;
      if (!directRefs && (file = Angular2InjectionUtils.getFirstInjectedFile(expression)) != null) {
        return file;
      }
      if (expression instanceof JSCallExpression) {
        JSExpression[] args = ((JSCallExpression)expression).getArguments();
        if (args.length == 1) {
          expression = args[0];
          directRefs = true;
        }
        else {
          return null;
        }
      }
      for (PsiReference ref : expression.getReferences()) {
        PsiElement el = ref.resolve();
        if (directRefs) {
          if (el instanceof PsiFile) {
            return (PsiFile)el;
          }
        }
        else if (el instanceof ES6ImportedBinding) {
          for (PsiElement importedElement : ((ES6ImportedBinding)el).findReferencedElements()) {
            if (importedElement instanceof PsiFile) {
              return (PsiFile)importedElement;
            }
          }
        }
      }
    }
    return null;
  }

  private static class FakeStringLiteral extends FakePsiElement {

    private final PsiElement myParent;
    private final String myValue;

    FakeStringLiteral(@NotNull PsiElement parent, @NotNull String value) {
      super();
      myParent = parent;
      myValue = StringUtil.unquoteString(value);
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    @Nullable
    @Override
    public String getText() {
      return myValue;
    }

    @Override
    public int getTextLength() {
      return myValue.length();
    }

    @Override
    public int getStartOffsetInParent() {
      throw new IllegalStateException();
    }
  }
}
