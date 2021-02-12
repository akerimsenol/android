/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild;

import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModelImpl.ABI_FILTERS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModelImpl.ARGUMENTS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModelImpl.CPP_FLAGS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModelImpl.C_FLAGS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModelImpl.TARGETS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBuildOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"abiFilters", property, ABI_FILTERS, VAR},
    {"abiFilters", atLeast(0), ABI_FILTERS, AUGMENT_LIST},
    {"arguments", property, ARGUMENTS, VAR},
    {"arguments", atLeast(0), ARGUMENTS, AUGMENT_LIST},
    {"cFlags", property, C_FLAGS, VAR},
    {"cFlags", atLeast(0), C_FLAGS, AUGMENT_LIST},
    {"cppFlags", property, CPP_FLAGS, VAR},
    {"cppFlags", atLeast(0), CPP_FLAGS, AUGMENT_LIST},
    {"targets", property, TARGETS, VAR},
    {"targets", atLeast(0), TARGETS, AUGMENT_LIST}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = ktsToModelNameMap;

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  protected AbstractBuildOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
