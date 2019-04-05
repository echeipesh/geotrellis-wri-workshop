package org.globalforestwatch.layers

class Ecozones(grid: String) extends StringLayer with OptionalILayer {

  val uri: String = s"$basePath/ecozones/$grid.tif"

  def lookup(value: Int): String = value match {
    case 1  => "Boreal coniferous forest"
    case 2  => "Boreal mountain system"
    case 3  => "Boreal tundra woodland"
    case 4  => "No data"
    case 5  => "Polar"
    case 6  => "Subtropical desert"
    case 7  => "Subtropical dry forest"
    case 8  => "Subtropical humid forest"
    case 9  => "Subtropical mountain system"
    case 10 => "Subtropical steppe"
    case 11 => "Temperate continental forest"
    case 12 => "Temperate desert"
    case 13 => "Temperate mountain system"
    case 14 => "Temperate oceanic forest"
    case 15 => "Temperate steppe"
    case 16 => "Tropical desert"
    case 17 => "Tropical dry forest"
    case 18 => "Tropical moist deciduous forest"
    case 19 => "Tropical mountain system"
    case 20 => "Tropical rainforest"
    case 21 => "Tropical shrubland"
    case 22 => "Water"
  }
}
