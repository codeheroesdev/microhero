name := "micro"
version := "0.1"
scalaVersion := "2.11.8"

bintrayOrganization := Some("codeheroes")
bintrayPackage := "micro"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

lazy val `micro` = project.in(file("."))
  .settings(resolvers ++= Dependencies.additionalResolvers)
  .settings(libraryDependencies ++= Dependencies.akkaDependencies)
  .settings(libraryDependencies ++= Dependencies.testDependencies)
