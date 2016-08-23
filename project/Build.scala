import java.io.File

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import play.Project._
import sbt.Keys._
import sbt._

object ApplicationBuild extends Build {
  val applicationName = "cloud"
  
  val appDependencies = Seq(
    // Add your project dependencies here,
    javaCore,
    javaJdbc,
    //javaEbean,
	cache,
	"commons-io" 			% 	 "commons-io" 				%   "2.3",
	"mysql"         		%    "mysql-connector-java"     %   "5.1.20"
  )
  
  val javaCommonsDependencies = Seq(
    javaCore,
	javaJdbc,
	cache,
	"commons-io" 			% 	 "commons-io" 				%   "2.3",
	"com.ning"				%	 "async-http-client"		%	"1.8.10"
  )
  
  val playProjects = new collection.mutable.ArrayBuffer[String]
  val groupProjecs = new collection.mutable.HashMap[String,Tuple2[Seq[String],Project]]
  
  def loadProject(baseDir:File) = {
	val xmlName = "plugin.xml"
	val mainProjects = baseDir.listFiles.filter(f => f.isDirectory && f.listFiles.exists(ff => ff.getName == xmlName))
	val pluginProjects = file("plugins").listFiles.filter(f => f.isDirectory && f.listFiles.exists(ff => ff.getName == xmlName))
	val allProjects = mainProjects ++ pluginProjects
	for(pDir <- allProjects){
		val xmlFile = new File(pDir,xmlName)
		val xmlRoot = xml.XML.loadFile(xmlFile)
		definedProject(groupProjecs,xmlRoot,pDir)
	}
	groupProjecs.foreach{case (name,modPart) =>
		val mod = modPart._2
		var depends:Seq[Project] = Seq.empty
		modPart._1.foreach{dependNames =>
			groupProjecs.get(dependNames) match {
				case Some(refProj) => depends = depends :+ refProj._2
				case None =>
			}
		}
		if(depends.length > 0){
			val dependsDep = depends.map{ p =>
				val toDep:ClasspathDep[ProjectReference] = p
				toDep
			}
			val newMod:Project = mod.dependsOn(dependsDep:_*)
			groupProjecs += (name -> new Tuple2(modPart._1,newMod))				
		}else{
			groupProjecs += (name -> new Tuple2(modPart._1,mod))
		}
	}
  }
  
  def definedProject(projects:collection.mutable.HashMap[String,Tuple2[Seq[String],Project]],xmlRoot:scala.xml.Node,path:File):Unit = {
		val name = (xmlRoot \ "name").text
		val version = (xmlRoot \ "version").text
		val pkgType = (xmlRoot \ "package").text
		val isPlugin = java.lang.Boolean.valueOf((xmlRoot \ "plugin").text)
		
		val dependencies = xmlRoot \ "dependencies" \ "dependency"
		var dependSeq:Seq[ModuleID] = Seq.empty
		var dependPlugin:Seq[String] = Seq.empty
		for(depend <- dependencies){
			val groupId = (depend \ "groupId").text
			val artifactId = (depend \ "artifactId").text
			val version = (depend \ "version").text
			val scope = (depend \ "scope").text
			val projectStr = (depend \ "project").text
			val exclusions = depend \ "exclusions" \ "exclusion"
			if(groupId.length > 0){
				if(scope != null &&  scope.length > 0){
					var dep = (groupId % artifactId % version % scope)
					for(excl <- exclusions){
						dep = dep.exclude((excl \ "groupId").text,(excl \ "artifactId").text)
					}
					dependSeq = dependSeq :+ dep
				}else{
					var dep = (groupId % artifactId % version)
					for(excl <- exclusions){
						dep = dep.exclude((excl \ "groupId").text,(excl \ "artifactId").text)
					}
					dependSeq = dependSeq :+ dep
				}
			}else if(projectStr.length > 0){
				dependPlugin = dependPlugin :+ projectStr
			}
		}
		var projt:Project = null
		if(pkgType.length <= 0 || pkgType == "play"){
			if(isPlugin){
				projt = PluginProject(name,version,path)
			}else{
				projt = MainProject(name,version,path)
			}
		}else{
			projt = JavaPluginProject(name,version,path)
		}
		if(dependSeq.length > 0){projt = projt.settings(libraryDependencies ++= dependSeq)}
		
		groupProjecs += (name -> new Tuple2(dependPlugin,projt))
  }
  
  def JavaPluginProject(name: String,version:String, dir:File): Project = {
	//playProjects += name
	play.Project(name,version,javaCommonsDependencies,path = dir)
      .settings(
		playPlugin :=true,
        crossPaths := false,
		organization := applicationName,
		sources in doc in Compile := List(),
		publishArtifact in (Compile, packageDoc) := false,
		publishArtifact in (Compile, packageSrc) := false,
		generateReverseRouter := false,
		Tasks.distWarTask,
		Tasks.distPackage,
		sourceDirectory in Compile <<= baseDirectory / "src",
		sourceDirectory in Test <<= baseDirectory / "src",

		confDirectory <<= baseDirectory / "conf",

		resourceDirectory in Compile <<= baseDirectory / "conf",

		scalaSource in Compile <<= baseDirectory / "src/main/scala",
		scalaSource in Test <<= baseDirectory / "src/test/scala",

		javaSource in Compile <<= baseDirectory / "src/main/java",
		javaSource in Test <<= baseDirectory / "src/test/java",
		
		javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8"),
		resolvers ++= Resolvers.wplatformReleases,
		publishTo := Some(Resolvers.wplatformPublish),
		credentials += publishCredentials
		)
  }
  
  def PluginProject(name: String,version:String, dir:File): Project = PlayProject(name,version,dir).settings(playPlugin :=true)
  def MainProject(name: String,version:String, dir:File): Project = PlayProject(name,version,dir)
  
  def PlayProject(name: String,version:String, dir:File): Project = {
    playProjects += name
    play.Project(name,version,appDependencies,path=dir)
      .settings(
        crossPaths := false,
		//skip in update := true, //取消依赖包检测
		organization := applicationName,
		sources in doc in Compile := List(),
		generateReverseRouter := false,
		publishArtifact in (Compile, packageDoc) := false,
		publishArtifact in (Compile, packageSrc) := false,
		Tasks.distWarTask,
		Tasks.distPackage,
		javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8", "-Xlint:-options"),
		resolvers ++= Resolvers.wplatformReleases,
		publishTo := Some(Resolvers.wplatformPublish),
		credentials += publishCredentials
		)
  }
  
  lazy val publishCredentials = {
	val cfile = Path.userHome / ".sbt" / "archiva_credentials"
	if(cfile.exists){
		Credentials(cfile)
	}else{
		Credentials("realm", "host", "userName", "c.passwd")
	}
  }

  /****************************Load The Project*******************************/
  loadProject(file("."))
  val modules = groupProjecs.values.map(_._2).toSeq
  //lazy val Root = sbt.Project("Root",file(".")) 
  override def projects = modules 
  /***************************************************************************/
  
 
  object Dependencies{
    val runtime = Seq(
			"mysql"         		%    "mysql-connector-java"     %   "5.1.20",
			"commons-io" 			% 	 "commons-io" 				%   "2.3",
			"org.freemarker" 		%    "freemarker" 				% 	"2.3.20",
			"com.fasterxml.uuid"    %    "java-uuid-generator"      %   "3.1.0"
	)
	val quartz = Seq(
			"org.quartz-scheduler"	%	"quartz"		%		"2.1.6"
	)
	val playJava = Seq(
			"junit"                             %    "junit-dep"                %   "4.10"  %  "test"

	)
  }

  object Tasks{
	val distPackage = dist in Universal <<= (dist in Universal) map{distFile =>
		val filename = distFile.getName
		val dirname = filename.replace(".zip","");
		val unzip = new File(distFile.getParent,dirname)
		val tozip = distFile
		IO.unzip(distFile,new File(distFile.getParent))
		val root = new File("").getAbsoluteFile
		
		val scripts = Path.allSubpaths(root / "scripts").map{ case(file , path) => file -> (dirname+"/"+ path)}
		val plugins = new collection.mutable.ArrayBuffer[(File,String)]
		Path.allSubpaths(unzip / "lib").foreach{
			case(file , path) => {
				if(isPlayPlugin(file,path)){
					plugins += (file -> (dirname+"/plugins/"+ path))
				}else if(isPlayLib(file,path)){
					plugins += (file -> (dirname+"/plugins/lib/"+ path))
				}else{
					plugins += (file -> (dirname+"/lib/"+ path))
				}
			} 
		}
		/*
		val plugins = Path.allSubpaths(unzip / "lib").filter(f => isPlayPlugin(f._1,f._2)).map{ case(file , path) => file -> (dirname+"/plugins/"+ path)}
		val libs = Path.allSubpaths(unzip / "lib").filter(f => isNotPlayLib(f._1,f._2)).map{ case(file , path) => file -> (dirname+"/lib/"+ path)}
		*/
		deleteUnuseJar(unzip)
		IO.zip(scripts ++ plugins,tozip)
		IO.delete(unzip)
		
		tozip
	}
	val distWar = TaskKey[File]("dist-war")
	val distWarTask = distWar <<= (dist in Universal) map{distFile => 
		val filename = distFile.getName
		val dirname = filename.replace(".zip","");
		val unzip = new File(distFile.getParent,dirname)
		val tozip = distFile
		IO.unzip(distFile,new File(distFile.getParent))
		val root = new File("").getAbsoluteFile
		
		//copy tomcat
		val tomcats = Path.allSubpaths(root / "tomcat").filter(f => f._1.getName.endsWith(".jar")).map{ case(file , path) => IO.copyFile(file,unzip / "lib" / file.getName)}
		val webxml = Path.allSubpaths(root / "tomcat").filter(f => f._1.getName.endsWith(".xml")).map{ case(file , path) => IO.copyFile(file,unzip / file.getName)}
		val webinfo = Path.allSubpaths(unzip).map{case(file,path) => file -> (dirname + "/WEB-INF/" + path)}
		
		IO.zip(webinfo,tozip)
		IO.delete(unzip)
		
		tozip
	}
	
  def isPlayPlugin(file:File,path:String) = isPlayLibWrap(file,path,true,false)	  
  def isPlayLib(file:File,path:String) = isPlayLibWrap(file,path,true,true)
  def isNotPlayLib(file:File,path:String) = isPlayLibWrap(file,path,false)
  
  lazy val ignoreCommonsJars = Seq("commons","supports")
  
  def isPlayLibWrap(file:File,path:String,isPlay:Boolean,isLib:Boolean=true):Boolean = {
  	var idx = file.getName.indexOf(applicationName)
	if(idx == 0){
		var partName = file.getName.substring(applicationName.length+1,file.getName.length)
		//idx = partName.lastIndexOf('-')
		//partName = partName.substring(0,idx)
		if(isLib){
			val exists = !ignoreCommonsJars.exists(n => partName.startsWith(n+"-"))
			if(isPlay){exists}else{!exists}
		}else{
			val exists = playProjects.exists(n => partName.startsWith(n+"-"))
			if(isPlay){exists}else{!exists}
		}
	}else{
		!isPlay
	}
  }
  
  def deleteUnuseJar(unzip:File) :Unit = {
	val prefix = Seq("org.seleniumhq.selenium.selenium","org.eclipse.jetty.jetty","org.fluentlenium.fluentlenium",
					"org.easytesting.fest","junit.junit","net.sourceforge.cssparser.cssparser","net.sourceforge.htmlunit.htmlunit","net.sourceforge.nekohtml.nekohtml",
					"net.java.dev.jna.jna","net.java.dev.jna.platform","org.scalaz.scalaz","org.specs2.specs2","com.novocode.junit-interface","com.typesafe.play.play-test")
	Path.allSubpaths(unzip / "lib").filter{file =>
		prefix.find(file._1.getName.startsWith(_)).isDefined
	}.foreach{file =>
		IO.delete(file._1)
	}
  }
  }
  
}

object Resolvers {
	val wplatformPublish = "Wplatform Releases Repository" at "http://182.254.150.71:8081/nexus/content/repositories/releases/"
	val wplatformReleases = Seq(wplatformPublish)
}