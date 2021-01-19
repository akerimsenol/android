android {
  packagingOptions {
    doNotStrip("doNotStrip1")
    doNotStrip("doNotStrip2")
    doNotStrip("doNotStrip3")
    exclude("exclude1")
    exclude("exclude2")
    exclude("exclude3")
    merges = mutableSetOf("merge1", "merge2")
    merge("merge3")
    pickFirsts = mutableSetOf("pickFirst1", "pickFirst2")
    pickFirst("pickFirst3")
  }
}
