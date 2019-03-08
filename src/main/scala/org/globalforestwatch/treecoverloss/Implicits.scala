package org.globalforestwatch.treecoverloss

import cats.Monoid
import geotrellis.raster.histogram.StreamingHistogram

/** Here we define ad-hoc interface implementations.
  * These are interfaces required to perform polygonalSummary on a Raster[Tile]
  */
object Implicits {
  /** Instance of interface that defines how to:
    * - get an empty/initial value of StreamingHistogram
    * - combine two instances of StreamingHistogram
    *
    * This allows using polygonalSummary methods without providing initial value like:
    *
    * {{{
    * val summary = raster.polygonalSummary(geometry)
    * }}}
    *
    * @see https://typelevel.org/cats/typeclasses/monoid.html
    */
  implicit val streamingHistogramMonoid: Monoid[StreamingHistogram] =
    new Monoid[StreamingHistogram] {
      def empty: StreamingHistogram = new StreamingHistogram(256)
      def combine(x: StreamingHistogram, y: StreamingHistogram): StreamingHistogram = x.merge(y)
    }
}
