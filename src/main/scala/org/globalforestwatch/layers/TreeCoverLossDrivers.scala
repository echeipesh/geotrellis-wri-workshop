package org.globalforestwatch.layers

class TreeCoverLossDrivers(grid: String)
    extends StringLayer
    with OptionalILayer {
  val uri: String = s"$basePath/drivers/$grid.tif"

  override def internalNoDataValue = 16

  def lookup(value: Int): String = value match {
    case 1 => "Commodity driven deforestation"
    case 2 => "Shifting agriculture"
    case 3 => "Forestry"
    case 4 => "Wildfire"
    case 5 => "Urbanization"
    case _ => null
  }
}
