/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class RenderSettings implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private int myMaxWidth;
  private int myMaxHeight;
  private WireframeMode myWireframeMode;

  // Constructs a default-initialized {@link RenderSettings}.
  public RenderSettings() {}


  public int getMaxWidth() {
    return myMaxWidth;
  }

  public RenderSettings setMaxWidth(int v) {
    myMaxWidth = v;
    return this;
  }

  public int getMaxHeight() {
    return myMaxHeight;
  }

  public RenderSettings setMaxHeight(int v) {
    myMaxHeight = v;
    return this;
  }

  public WireframeMode getWireframeMode() {
    return myWireframeMode;
  }

  public RenderSettings setWireframeMode(WireframeMode v) {
    myWireframeMode = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service","RenderSettings","","");

  static {
    Namespace.register(Klass.INSTANCE);
    ENTITY.setFields(new Field[]{
      new Field("MaxWidth", new Primitive("uint32", Method.Uint32)),
      new Field("MaxHeight", new Primitive("uint32", Method.Uint32)),
      new Field("WireframeMode", new Primitive("WireframeMode", Method.Int32)),
    });
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new RenderSettings(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      RenderSettings o = (RenderSettings)obj;
      e.uint32(o.myMaxWidth);
      e.uint32(o.myMaxHeight);
      o.myWireframeMode.encode(e);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      RenderSettings o = (RenderSettings)obj;
      o.myMaxWidth = d.uint32();
      o.myMaxHeight = d.uint32();
      o.myWireframeMode = WireframeMode.decode(d);
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
