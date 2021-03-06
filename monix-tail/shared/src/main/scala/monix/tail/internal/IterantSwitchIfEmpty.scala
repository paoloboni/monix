/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.tail.internal

import cats.syntax.functor._
import cats.effect.Sync
import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantSwitchIfEmpty {
  def apply[F[_], A](primary: Iterant[F, A], backup: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {
    def loop(source: Iterant[F, A]): Iterant[F, A] =
      try source match {
        case Suspend(rest, stop) => Suspend(rest.map(loop), stop)
        case NextBatch(batch, rest, stop) =>
          val cursor = batch.cursor()
          if (!cursor.hasNext()) {
            Suspend(rest.map(loop), stop)
          } else {
            NextCursor(cursor, rest, stop)
          }

        case NextCursor(cursor, rest, stop) if !cursor.hasNext() =>
          Suspend(rest.map(loop), stop)

        case Halt(None) => backup
        case _ => source
      } catch {
        case ex if NonFatal(ex) =>
          val stop = source.earlyStop
          Suspend(stop.as(Halt(Some(ex))), stop)
      }

    primary match {
      case NextBatch(_, _, _) | NextCursor(_, _, _) =>
        Suspend(F.delay(loop(primary)), primary.earlyStop)
      case _ => loop(primary)
    }
  }
}
