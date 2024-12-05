package models

import scala.language.implicitConversions

trait FolderOrFileContents

object FolderOrFileContents {
  // implicit conversion: when FolderOrFileContents is expected, automatically try to call the below if needed
  implicit def repoItemListToCaseClass(items: Seq[RepoItem]): FolderOrFileContents = RepoItemList(items)
}
