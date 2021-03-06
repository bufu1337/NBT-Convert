package com.infinity88.nbt2ascii

import java.io._
import java.nio.file.{Files, Path, Paths}

import com.flowpowered.nbt.stream.NBTInputStream
import com.flowpowered.nbt.{CompoundTag, ListTag, Tag}

import scala.collection.JavaConverters._
import scala.collection.mutable

object Main {
  val DIRT = "minecraft:dirt"
  val AIR = "minecraft:air"
  val STONE = "minecraft:stone"

  val subs = Map(
     { "minecraft:grass" -> DIRT }
    ,{ "minecraft:coal_ore" -> STONE }
    ,{ "minecraft:cocoa" -> AIR }
    ,{ "minecraft:gravel" -> STONE }
    ,{ "minecraft:red_flower" -> AIR }
    ,{ "minecraft:yellow_flower" -> AIR }
    ,{ "minecraft:red_mushroom" -> AIR }
    ,{ "minecraft:brown_mushroom" -> AIR }
    ,{ "minecraft:snow_layer" -> AIR }
    ,{ "minecraft:tallgrass" -> AIR }
    ,{ "minecraft:vine" -> AIR }
  )

  def main(args: Array[String]): Unit = {
    //val inputPath = "sample-structures/ochill.nbt"
    //val inputPath = "sample-structures/worldtree.nbt"
    //val foldin = "Y:/Minecraft/NBT/In"
    //val foldout = "Y:/Minecraft/NBT/Out"
    val foldin = "C:/Privat/NBTConvert/In"
    val foldout = "C:/Users/alexandersk/workspace/OC-Scripts/src/Builder/Models/"
    val bla: Array[File] = getRecursiveListOfFiles(new File(foldin))
    println(bla)
    val bla2: List[String] = getListOfSubDirectories(new File(foldin))
    println(bla2)
    for(x <- bla){
      if(x.isFile){
        println(x)
        
        val inputPath = x.toString()
      
        val levelName = new File(inputPath).getName.takeWhile(c => c != '.')
        val foldername = x.toString().substring(foldin.length, x.toString().length - levelName.length -5)
                
        val outputLevelsDirectory = Paths.get(s"${foldout}${foldername}/${levelName}")
        outputLevelsDirectory.toFile.mkdirs()
      
        val outputModelPath = Paths.get(s"${foldout}${foldername}/${levelName}.model")
        if(!(Files.exists(outputModelPath)) && !(Files.exists(Paths.get(s"${foldout}/${levelName}.lua")))){
          println("Input")
          println(inputPath)
          println("level Name")
          println(levelName)
          println("folder name")
          println(foldername)
          val rawData = Files.readAllBytes(Paths.get(inputPath))
          val nbtInputStream = new NBTInputStream(new ByteArrayInputStream(rawData))
        
          val structureRoot = nbtInputStream.readTag().asInstanceOf[CompoundTag].getValue
        
          val sizeTag = structureRoot.get("size").asInstanceOf[ListTag[Tag[Int]]]
          val sizeTags: List[Tag[Int]] = sizeTag.getValue.asScala.toList
          val sizeInfo = sizeTags.map(t => t.getValue)
          val (depth, height, width) = (sizeInfo(0), sizeInfo(1), sizeInfo(2))
        
          // read names from the palette (index is referenced by block state)
          val paletteTag = structureRoot.get("palette").asInstanceOf[ListTag[CompoundTag]]
          val paletteEntries = paletteTag.getValue.asScala.toList
          val paletteList = paletteEntries.map(e => e.getValue.get("Name").asInstanceOf[Tag[String]].getValue)
            .map(name => subs.getOrElse(name, name))
        
          val blocksTag = structureRoot.get("blocks").asInstanceOf[ListTag[CompoundTag]]
          val blockTags = blocksTag.getValue.asScala.toList
        
          val materialMaps = generateMaterialMaps(paletteList.distinct)
        
          val layout = Array.ofDim[Char](height, depth, width)
          val middleDropPoint = (depth/2, width/2)
        
          for (blockTag <- blockTags) {
            val map = blockTag.getValue
            val state = map.get("state").asInstanceOf[Tag[Int]].getValue
            val blockType = paletteList(state)
            val blockMoniker = materialMaps._1.getOrElse(blockType, ' ')
        
            val posTag = map.get("pos").asInstanceOf[ListTag[Tag[Int]]]
            val positions = posTag.getValue.asScala.toList.map(t => t.getValue)
            val (x, y, z) = (positions(0), positions(1), positions(2))
        
            layout(y)(x)(z) = blockMoniker
          }
        
          trimAir(layout, middleDropPoint)
        
          for (i <- 0 until height) {
            // output each layer as a file
            val levelLayout = layout(i)
            writeLevelFile(outputLevelsDirectory, i + 1, levelLayout)
          }
        
          // output the level.model file
          writeModelFile(outputModelPath, levelName, materialMaps, middleDropPoint, layout)
          println("Finished file")
        }
      }
    }
    println("Finished Process")
  }
  
  def getListOfFiles(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
        d.listFiles.filter(_.isFile).toList 
        /*val xs = */
    } else{
        List[File]()
    }
  }
  def getListOfSubDirectories(dir: File): List[String] =
    dir.listFiles
       .filter(_.isDirectory)
       .map(_.getName)
       .toList
  def getListOfDirs(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
        d.listFiles.filter(_.isDirectory).toList
    } else{
        List[File]()
    }
  }
  
def getRecursiveListOfFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    these ++ these.filter(_.isDirectory).flatMap(getRecursiveListOfFiles)
}

  private def writeModelFile(modelPath: Path,
                             levelName: String,
                             materialMaps: (Map[String,Char], Map[Char,String]),
                             dropPoint: (Int, Int),
                             layout: Array[Array[Array[Char]]]): Unit = {
    val printWriter = new PrintWriter(modelPath.toString)
    printWriter.println(s"title:string=${levelName}")
    printWriter.println(s"author:string=nbt2ascii")
    printWriter.println(s"mats:table={")
    // material lookup by moniker
    materialMaps._2.toList.sortBy(e => e._2).foreach(entry => {
      printWriter.println(s"  ${entry._1}:string=${entry._2}")
    })
    printWriter.println(s"}")
    // Assume a start point at the bottom right of the first level
    // Authors can change it after generation easily enough.
    printWriter.println(s"startPoint:list=[")
    printWriter.println(s"  number=${layout(0).length + 1}")
    printWriter.println(s"  number=${layout(0).last.length}")
    printWriter.println(s"  number=1")
    printWriter.println(s"  string=^")
    printWriter.println(s"]")
    // Make the droppoints in the middle. Ideally we'd calculate them for each level though.
    printWriter.println(s"defaultDropPoint:list=[")
    printWriter.println(s"  number=${dropPoint._1}")
    printWriter.println(s"  number=${dropPoint._2}")
    printWriter.println(s"]")
    printWriter.println(s"levels:list=[")
    var levelNum = 0
    for (level <- layout) {
      levelNum = levelNum + 1
      val matCounts = getMatCounts(level, materialMaps)
      printWriter.println(s"  table={")
      printWriter.println(s"    name:string=level ${levelNum}")
      printWriter.println(s"    blocks:string=@github")
      printWriter.println(s"    matCounts:table={")
      for (mat <- matCounts) {
        printWriter.println(s"      ${mat._1}:number=${mat._2}")
      }
      printWriter.println(s"    }")
      printWriter.println(s"  }")
    }
    printWriter.println(s"]")
    printWriter.close()
  }

  private def getMatCounts(level: Array[Array[Char]], materialMaps: (Map[String,Char], Map[Char,String])): Map[String, Int] = {
    val counts = mutable.Map[String, Int]()
    for (row <- level) {
      for (col <- row) {
        if (materialMaps._2.contains(col)) {
          val material = materialMaps._2(col)
          counts(material) = counts.getOrElse(material, 0) + 1
        }
      }
    }
    counts.toMap
  }

  private def trimAir(layout: Array[Array[Array[Char]]], dropPoint: (Int, Int)): Unit = {
    for (level <- layout) {
      for (row <- level) {
        // replace leading and trailing 'air' with DND blocks
        // except take care to make sure there's at least an airway from the droppoint
        val firstBlockIndex = row.indexWhere(c => c != 0)
        val firstBlock = Math.min(firstBlockIndex, dropPoint._2) match {
          case -1 => dropPoint._2
          case i => i
        }
        val lastBlock = Math.max(row.lastIndexWhere(c => c != 0), dropPoint._2)
        for (i <- 0 until firstBlock) {
          row(i) = '-'
        }
        // tailing...
        for (i <- lastBlock + 1 until row.length) {
          row(i) = '-'
        }
      }

      // this level is done, but we can prune leading and trailing rows that are nothing but an empty
      // airway to the drop point...
      // fix leading rows...
      var foundNonEmpty = false
      for (i <- 0 until dropPoint._1) {
        if (!foundNonEmpty) {
          val row = level(i)
          val isEmptyRow = (row.count(c => c == '-') == row.length - 1) && (row.count(c => c == 0) == 1)
          if (isEmptyRow) {
            row(row.indexOf(0)) = '-'
          } else {
            foundNonEmpty = true
          }
        }
      }

      // fix trailing rows..
      foundNonEmpty = false
      for (i <- level.length - 1 until dropPoint._1 by -1) {
        if (!foundNonEmpty) {
          val row = level(i)
          val isEmptyRow = (row.count(c => c == '-') == row.length - 1) && (row.count(c => c == 0) == 1)
          if (isEmptyRow) {
            row(row.indexOf(0)) = '-'
          } else {
            foundNonEmpty = true
          }
        }
      }
    }
  }

  private def writeLevelFile(directory: Path, levelNum: Int, layout: Array[Array[Char]]): Unit = {
    val printWriter = new PrintWriter(directory.resolve("%03d".format(levelNum)).toString)
    for (row <- layout) {
      for (col <- row) {
        printWriter.print(if (col == 0) ' ' else col)
      }
      printWriter.println()
    }
    printWriter.close()
  }

  def generateMaterialMaps(paletteList: List[String]): (Map[String, Char], Map[Char, String]) = {
    // artificially put vanilla blocks first as they are usually the most important and should get
    // priority when assigning monikers to them.
    val sortedPaletteList = paletteList.sortBy(p => (if (p.startsWith("minecraft:")) "a" else "b", p))
    val monikerMap = mutable.Map[String, Char]()
    val materialsMap = mutable.Map[Char, String]()

    for (name <- sortedPaletteList) {
      getMoniker(materialsMap, name) match {
        case Some(moniker) =>
          materialsMap.put(moniker, name)
          monikerMap.put(name, moniker)
        case _ =>
      }
    }
    (monikerMap.toMap, materialsMap.toMap)
  }

  def getMoniker(mats: mutable.Map[Char, String], name: String): Option[Char] = {
    if (name == "minecraft:air") {
      // todo: a blacklist could go here
      return None
    }

    val namePattern = "(.*):(.*)".r
    val moniker = name match {
      case namePattern(_, t) =>
        // use the first letter in the name which isn't already used,
        // except for a few special characters that aren't allowed as monikers.
        for (c <- t) {
          if (!Set('v','<,'>','^','-',' ').contains(c)) {
            if (!mats.contains(c)) {
              return Some(c)
            }
          }
        }
        None
      case _ =>
        None
    }

    moniker.getOrElse(getMoniker(mats, "default:abcdefghijklmnopqrstuvwxyz0123456789"))
  }
}
