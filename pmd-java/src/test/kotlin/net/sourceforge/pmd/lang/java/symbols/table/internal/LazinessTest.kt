package net.sourceforge.pmd.lang.java.symbols.table.internal

import io.kotlintest.specs.FunSpec
import javasymbols.testdata.Statics
import net.sourceforge.pmd.lang.ast.test.shouldBe
import net.sourceforge.pmd.lang.java.symbols.JMethodSymbol
import net.sourceforge.pmd.lang.java.symbols.JTypeDeclSymbol
import net.sourceforge.pmd.lang.java.symbols.JValueSymbol
import net.sourceforge.pmd.lang.java.symbols.internal.classSym
import net.sourceforge.pmd.lang.java.symbols.internal.testSymFactory
import net.sourceforge.pmd.lang.java.symbols.table.JSymbolTable
import net.sourceforge.pmd.lang.java.symbols.table.ResolveResult
import java.util.stream.Stream
import kotlin.test.assertEquals

/**
 * Asserts that queries don't travel up the table stack more than necessary.
 *
 * @author Clément Fournier
 * @since 7.0.0
 */
class LazinessTest : FunSpec({

    fun <T> lazinessTest(
            dummyTableConstructor: (JSymbolTable, SymbolTableResolveHelper, () -> T?) -> JSymbolTable,
            testedMethod: (JSymbolTable, String) -> T?,
            resultGetter: () -> T) {

        val myExpectedResult = resultGetter()
        val dummyResolveHelper = testResolveHelper("")

        val top: JSymbolTable = dummyTableConstructor(EmptySymbolTable.getInstance(), dummyResolveHelper) {
            throw AssertionError("The parent shouldn't have been queried")
        }

        val bottom: JSymbolTable = dummyTableConstructor(top, dummyResolveHelper) { resultGetter() }

        assertEquals(myExpectedResult, testedMethod(bottom, ""))
    }

    test("Test resolveTypeName doesn't query parents if not needed") {
        lazinessTest(::MyTypeTable, { t, n -> t.resolveTypeName(n).result }) {
            testSymFactory.getClassSymbol(LazinessTest::class.java)!!
        }
    }


    test("Test resolveValueName doesn't query parents if not needed") {
        lazinessTest(::MyValueTable, { t, n -> t.resolveValueName(n).result }) {
            classSym(Statics::class.java)!!.getDeclaredField("PUBLIC_FIELD")
        }
    }


    test("Test resolveMethodName doesn't trigger evaluation of parent streams") {
        lazinessTest<JMethodSymbol>(::MyMethodTable, { t, n -> t.resolveMethodName(n).findFirst().get() }) {
            classSym(Statics::class.java)!!.getDeclaredMethods("publicMethod")[0]
        }
    }


    test("Test resolveMethodName evaluates parent streams if needed") {

        val dummyResolveHelper = testResolveHelper("")

        val top: JSymbolTable = MyMethodTable(EmptySymbolTable.getInstance(), dummyResolveHelper) {
            classSym(Statics::class.java)!!.getDeclaredMethods("publicMethod")[0]
        }

        val bottom: JSymbolTable = MyMethodTable(top, dummyResolveHelper) {
            null
        }

        bottom.resolveMethodName("publicMethod").findFirst()::isPresent shouldBe true
    }

})

private class MyTypeTable(
        parent: JSymbolTable,
        resolveHelper: SymbolTableResolveHelper,
        private val typeSymbolGetter: () -> JTypeDeclSymbol?
) : AbstractSymbolTable(parent, resolveHelper) {

    override fun resolveTypeNameImpl(simpleName: String): ResolveResult<JTypeDeclSymbol> =
            ResolveResultImpl.ClassResolveResult(typeSymbolGetter(), this, null)

}

private class MyValueTable(
        parent: JSymbolTable,
        resolveHelper: SymbolTableResolveHelper,
        private val valueSymbolGetter: () -> JValueSymbol?
) : AbstractSymbolTable(parent, resolveHelper) {

    override fun resolveValueNameImpl(simpleName: String): ResolveResult<JValueSymbol>? =
            ResolveResultImpl.ValueResolveResult(valueSymbolGetter(), this, null)


}

private class MyMethodTable(parent: JSymbolTable, resolveHelper: SymbolTableResolveHelper,
                            private val methodSymbolGetter: () -> JMethodSymbol?) : AbstractSymbolTable(parent, resolveHelper) {

    override fun resolveMethodNameImpl(simpleName: String): Stream<JMethodSymbol>? =
            methodSymbolGetter()?.let { Stream.of(it) } ?: Stream.empty()

}
