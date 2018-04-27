package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.SetUtil;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Java 9 has made try-with-resources much cleaner.
 */
public abstract class TryResourcesTransformerBase implements StructuredStatementTransformer {

    private final ClassFile classFile;

    protected TryResourcesTransformerBase(ClassFile classFile) {
        this.classFile = classFile;
    }

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (in instanceof StructuredTry) {
            Op04StructuredStatement container = in.getContainer();
            StructuredTry structuredTry = (StructuredTry)in;
            Op04StructuredStatement finallyBlock = structuredTry.getFinallyBlock();
            ResourceMatch match = findResourceFinally(finallyBlock);
            if (match != null) {
                // Ok, now we have to find the initialisation of the closable.
                rewriteTry(structuredTry, scope, match);
            }
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }

    // Now we have to walk back from the try statement, finding the declaration of the resource.
    // the declaration MUST not be initialised from something which is subsequently used before the try statement.
    private void rewriteTry(StructuredTry structuredTry, StructuredScope scope, ResourceMatch resourceMatch) {
        List<Op04StructuredStatement> preceeding = scope.getPrecedingInblock(1, 2);
        // seatch backwards for a definition of resource.
        LValue resource = resourceMatch.resource;
        Op04StructuredStatement autoAssign = findAutoclosableAssignment(preceeding, resource);
        if (autoAssign == null) return;
        StructuredAssignment assign = (StructuredAssignment)autoAssign.getStatement();
        autoAssign.nopOut();
        structuredTry.setFinally(null);
        structuredTry.setResources(Collections.singletonList(assign));
        if (resourceMatch.resourceMethod != null) {
            resourceMatch.resourceMethod.hideSynthetic();
        }
    }

    private Op04StructuredStatement findAutoclosableAssignment(List<Op04StructuredStatement> preceeding, LValue resource) {
        LValueUsageCheckingRewriter usages = new LValueUsageCheckingRewriter();
        for (int x=preceeding.size()-1;x >= 0;--x) {
            Op04StructuredStatement stm = preceeding.get(x);
            StructuredStatement structuredStatement = stm.getStatement();
            if (structuredStatement.isScopeBlock()) return null;
            if (structuredStatement instanceof StructuredAssignment) {
                StructuredAssignment structuredAssignment = (StructuredAssignment)structuredStatement;

                if (structuredAssignment.isCreator(resource)) {
                    // get all values used in this, check they were not subsequently used.
                    LValueUsageCheckingRewriter check = new LValueUsageCheckingRewriter();
                    structuredAssignment.rewriteExpressions(check);
                    if (SetUtil.hasIntersection(usages.used, check.used)) return null;
                    return stm;
                }
                structuredStatement.rewriteExpressions(usages);
            }
        }
        return null;
    }

    protected ClassFile getClassFile() {
        return classFile;
    }

    private static class LValueUsageCheckingRewriter extends AbstractExpressionRewriter {
        final Set<LValue> used = SetFactory.newSet();
        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            used.add(lValue);
            return lValue;
        }
    }

    // If the finally block is
    // if (autoclosable != null) {
    //    close(exception, autoclosable)
    // }
    //
    // or
    //
    // close(exception, autoclosable)
    //
    // we can lift the autocloseable into the try.
    protected abstract ResourceMatch findResourceFinally(Op04StructuredStatement finallyBlock);

    protected static class ResourceMatch
    {
        final Method resourceMethod;
        final LValue resource;
        final LValue throwable;

        public ResourceMatch(Method resourceMethod, LValue resource, LValue throwable) {
            this.resourceMethod = resourceMethod;
            this.resource = resource;
            this.throwable = throwable;
        }
    }

    protected static class TryResourcesMatchResultCollector implements MatchResultCollector {
        StaticFunctionInvokation fn;
        LValue resource;
        LValue throwable;

        @Override
        public void clear() {
            fn = null;
            throwable = resource = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {

        }

        @Override
        public void collectStatementRange(String name, MatchIterator<StructuredStatement> start, MatchIterator<StructuredStatement> end) {

        }

        private StaticFunctionInvokation getFn(WildcardMatch wcm, String name) {
            WildcardMatch.StaticFunctionInvokationWildcard staticFunction = wcm.getStaticFunction(name);
            if (staticFunction == null) return null;
            return staticFunction.getMatch();
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            fn = getFn(wcm, "fn");
            if (fn == null) {
                fn = getFn(wcm, "fn2");
            }
            if (fn == null) {
                fn = getFn(wcm, "fn3");
            }
            resource = wcm.getLValueWildCard("resource").getMatch();
            throwable = wcm.getLValueWildCard("throwable").getMatch();
        }
    }
}