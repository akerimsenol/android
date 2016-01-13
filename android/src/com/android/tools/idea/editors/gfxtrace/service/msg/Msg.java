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
package com.android.tools.idea.editors.gfxtrace.service.msg;

import com.android.tools.idea.editors.gfxtrace.service.stringtable.Node;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;

import java.io.IOException;

public final class Msg implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private String myIdentifier;
  private LinkedHashMap<String, BinaryObject> myArguments;

  // Constructs a default-initialized {@link Msg}.
  public Msg() {}


  public String getIdentifier() {
    return myIdentifier;
  }

  public Msg setIdentifier(String v) {
    myIdentifier = v;
    return this;
  }

  public LinkedHashMap<String, BinaryObject> getArguments() {
    return myArguments;
  }

  public Msg setArguments(LinkedHashMap<String, BinaryObject> v) {
    myArguments = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("msg", "Msg", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Identifier", new Primitive("string", Method.String)),
      new Field("Arguments", new Map("", new Primitive("string", Method.String), new Interface("binary.Object"))),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>

  /**
   * Returns the message as a string without any rich-formatting.
   */
  public String getString() {
    StringTable stringTable = StringTable.getCurrent();
    if (stringTable != null) {
      Node node = stringTable.get(myIdentifier);
      if (node != null) {
        return node.getString(myArguments);
      }
    }
    return myIdentifier;
  }

  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new Msg(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Msg o = (Msg)obj;
      e.string(o.myIdentifier);
      e.uint32(o.myArguments.size());
      for (java.util.Map.Entry<String, BinaryObject> entry : o.myArguments.entrySet()) {
        e.string(entry.getKey());
        e.object(entry.getValue());
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Msg o = (Msg)obj;
      o.myIdentifier = d.string();
      o.myArguments = new LinkedHashMap<String, BinaryObject>();
      int size = d.uint32();
      for (int i = 0; i < size; i++) {
        String key = d.string();
        BinaryObject value = d.object();
        o.myArguments.put(key, value);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
