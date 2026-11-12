package android.content
/** JVM stub so the pure game engine compiles outside Android (tools/simharness only). */
class AssetStub { fun open(name: String): java.io.InputStream = error("stub $name") }
open class Context { open val assets: AssetStub get() = AssetStub() }
