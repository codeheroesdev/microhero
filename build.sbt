name := "microhero"
version := "0.1"
scalaVersion := "2.11.8"

bintrayOrganization := Some("codeheroes")
bintrayPackage := "microhero"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

lazy val `microhero` = project.in(file("."))
  .settings(resolvers ++= Dependencies.additionalResolvers)
  .settings(libraryDependencies ++= Dependencies.akkaDependencies)
  .settings(libraryDependencies ++= Dependencies.testDependencies)
