package org.globalforestwatch.layers

trait TreeCoverDensity extends IntegerLayer with RequiredILayer {

  override val externalNoDataValue: Integer = 0

  override def lookup(value: Int): Integer = {
    if (value <= 10) 0
    else if (value <= 15) 10
    else if (value <= 20) 15
    else if (value <= 25) 20
    else if (value <= 30) 25
    else if (value <= 50) 30
    else if (value <= 75) 50
    else 75
  }
}

class TreeCoverDensity2000(grid: String) extends TreeCoverDensity {
  val uri: String = s"$basePath/tcd_2000/$grid.tif"
}

class TreeCoverDensity2010(grid: String) extends TreeCoverDensity {
  val uri: String = s"$basePath/tcd_2010/$grid.tif"
}
