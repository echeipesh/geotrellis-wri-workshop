package org.globalforestwatch.layers

class IntactForestLandscapes(grid: String)
    extends IntegerLayer
    with OptionalILayer {
  val uri: String = s"$basePath/ifl/$grid.tif"
}
