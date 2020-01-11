/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.symbols.table.internal;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.symbols.JClassSymbol;
import net.sourceforge.pmd.lang.java.symbols.JMethodSymbol;
import net.sourceforge.pmd.lang.java.symbols.JTypeDeclSymbol;
import net.sourceforge.pmd.lang.java.symbols.JValueSymbol;
import net.sourceforge.pmd.lang.java.symbols.table.JSymbolTable;
import net.sourceforge.pmd.lang.java.symbols.table.ResolveResult;


/**
 * Base implementation.
 *
 * @since 7.0.0
 */
abstract class AbstractSymbolTable implements JSymbolTable {

    /** Additional info about the context. */
    final SymbolTableResolveHelper myResolveHelper;
    private final JSymbolTable myParent;

    AbstractSymbolTable(JSymbolTable parent, SymbolTableResolveHelper helper) {
        this.myParent = parent;
        this.myResolveHelper = helper;
    }


    @Override
    @NonNull
    public final JSymbolTable getParent() {
        return myParent;
    }


    @NonNull
    @Override
    public final ResolveResult<JTypeDeclSymbol> resolveTypeName(String simpleName) {
        @Nullable ResolveResult<JTypeDeclSymbol> result = resolveTypeNameImpl(simpleName);
        return result != null ? result : myParent.resolveTypeName(simpleName);
    }


    @NonNull
    @Override
    public final ResolveResult<JValueSymbol> resolveValueName(String simpleName) {
        @Nullable ResolveResult<JValueSymbol> result = resolveValueNameImpl(simpleName);
        return result != null ? result : myParent.resolveValueName(simpleName);
    }


    @Override
    public final Stream<JMethodSymbol> resolveMethodName(String simpleName) {
        // This allows the stream contributed by the parent to be resolved lazily,
        // ie not evaluated unless the stream contributed by this table runs out of values,
        // a behaviour that Stream.concat can't provide

        return Stream.<Supplier<Stream<JMethodSymbol>>>of(
            () -> resolveMethodNameImpl(simpleName),
            () -> myParent.resolveMethodName(simpleName)
        ).flatMap(Supplier::get);
    }


    /** Finds the matching methods among the declarations tracked by this table without asking the parent. */
    protected Stream<JMethodSymbol> resolveMethodNameImpl(String simpleName) {
        return Stream.empty();
    }

    // We could internally avoid using Optional to reduce the number of created optionals as an optimisation


    /**
     * Finds a type name among the declarations tracked by this table without asking the parent.
     */
    protected @Nullable ResolveResult<JTypeDeclSymbol> resolveTypeNameImpl(String simpleName) {
        return ResolveResultImpl.failed();
    }


    /** Finds a value among the declarations tracked by this table without asking the parent. */
    protected @Nullable ResolveResult<JValueSymbol> resolveValueNameImpl(String simpleName) {
        return ResolveResultImpl.failed();
    }


    /**
     * Tries to load a class and logs it if it is not found.
     *
     * @param anImport Node owning the warning
     * @param fqcn     Binary name of the class to load
     *
     * @return The class, or null if it couldn't be resolved
     */
    @Nullable
    final JClassSymbol loadClassReportFailure(ASTImportDeclaration anImport, String fqcn) {
        JClassSymbol loaded = myResolveHelper.loadClassOrFail(fqcn);
        if (loaded == null) {
            myResolveHelper.getLogger().warning(anImport, SemanticChecksLogger.CANNOT_FIND_CLASSPATH_SYMBOL, fqcn);
        }

        return loaded;
    }


    /**
     * Tries to load a class, not logging failure.
     *
     * @param canonicalName Canonical name of the class to load
     *
     * @return The class, or null if it couldn't be resolved
     */
    @Nullable
    final JClassSymbol loadClassIgnoreFailure(String canonicalName) {
        return myResolveHelper.loadClassOrFail(canonicalName);
    }

    /**
     * Returns true if this table doesn't contain any information, and
     * can be eliminated from the stack entirely.
     */
    boolean isPrunable() {
        // TODO would be better to conside the three channels separate.
        //  That way local scopes with no class declaration skip directly to type declaration scope
        return false;
    }

}
