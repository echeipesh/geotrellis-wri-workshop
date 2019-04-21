package org.globalforestwatch.layers

class TigerLandscapes(grid: String) extends BooleanLayer with OptionalILayer {
  val uri: String =
    s"$basePath/tiger_landscapes/$grid.tif"
}
