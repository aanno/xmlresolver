package org.xmlresolver.catalog.query;

import org.xmlresolver.CatalogManager;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.catalog.entry.Entry;
import org.xmlresolver.catalog.entry.EntryCatalog;
import org.xmlresolver.catalog.entry.EntryDelegateSystem;
import org.xmlresolver.catalog.entry.EntryRewriteSystem;
import org.xmlresolver.catalog.entry.EntrySystem;
import org.xmlresolver.catalog.entry.EntrySystemSuffix;
import org.xmlresolver.utils.URIUtils;

import java.net.URI;
import java.util.ArrayList;

public class QuerySystem extends QueryCatalog {
    public final String systemId;

    public QuerySystem(String systemId) {
        super();
        this.systemId = systemId;
    }

    @Override
    public QueryResult lookup(CatalogManager manager, EntryCatalog catalog) {
        String compareSystem = manager.normalizedForComparison(systemId);

        String osname = System.getProperty("os.name").toLowerCase();
        boolean ignoreFScase = osname.contains("windows") || osname.contains("mac");
        String lowerCaseSystemId = compareSystem.toLowerCase();

        // <system>
        for (Entry raw : catalog.entries(Entry.Type.SYSTEM)) {
            EntrySystem entry = (EntrySystem) raw;
            String entrySystem = manager.normalizedForComparison(entry.systemId);
            if (entrySystem.equals(compareSystem) || (ignoreFScase && entrySystem.equalsIgnoreCase(compareSystem))) {
                return new QueryResult(entry.uri);
            }
        }

        // <rewriteSystem>
        EntryRewriteSystem rewrite = null;
        String rewriteStart = null;
        for (Entry raw : catalog.entries(Entry.Type.REWRITE_SYSTEM)) {
            EntryRewriteSystem entry = (EntryRewriteSystem) raw;
            String compareStart = manager.normalizedForComparison(entry.systemIdStart);
            if (compareSystem.startsWith(compareStart) || (ignoreFScase && lowerCaseSystemId.startsWith(compareStart.toLowerCase()))) {
                if (rewrite == null || compareStart.length() > rewriteStart.length()) {
                    rewrite = entry;
                    rewriteStart = compareStart;
                }
            }
        }
        if (rewrite != null) {
            URI resolved = URIUtils.resolve(rewrite.rewritePrefix, compareSystem.substring(rewriteStart.length()));
            return new QueryResult(resolved);
        }

        // <systemSuffix>
        EntrySystemSuffix suffix = null;
        String systemSuffix = null;
        for (Entry raw : catalog.entries(Entry.Type.SYSTEM_SUFFIX)) {
            EntrySystemSuffix entry = (EntrySystemSuffix) raw;
            String compareSuffix = manager.normalizedForComparison(entry.systemIdSuffix);
            if (compareSystem.endsWith(compareSuffix) || (ignoreFScase && lowerCaseSystemId.endsWith(compareSuffix.toLowerCase()))) {
                if (suffix == null || compareSuffix.length() > systemSuffix.length()) {
                    suffix = entry;
                    systemSuffix = compareSuffix;
                }
            }
        }
        if (suffix != null) {
            return new QueryResult(suffix.uri);
        }

        // <delegateSystem>
        ArrayList<EntryDelegateSystem> delegated = new ArrayList<>();
        for (Entry raw : catalog.entries(Entry.Type.DELEGATE_SYSTEM)) {
            EntryDelegateSystem entry = (EntryDelegateSystem) raw;
            String delegateStart = manager.normalizedForComparison(entry.systemIdStart);
            if (compareSystem.startsWith(delegateStart) || (ignoreFScase && lowerCaseSystemId.startsWith(delegateStart.toLowerCase()))) {
                int pos = 0;
                while (pos < delegated.size()
                        && delegateStart.length() <= manager.normalizedForComparison(delegated.get(pos).systemIdStart).length()) {
                    pos += 1;
                }
                delegated.add(pos, entry);
            }
        }
        if (!delegated.isEmpty()) {
            ArrayList<URI> catalogs = new ArrayList<>();
            for (EntryDelegateSystem entry : delegated) {
                catalogs.add(entry.catalog);
            }
            return new QueryDelegateSystem(systemId, catalogs);
        }

        if (manager.getResolverConfiguration().getFeature(ResolverFeature.URI_FOR_SYSTEM)) {
            QueryUri query = new QueryUri(systemId);
            return query.lookup(manager, catalog);
        }

        return QueryResult.EMPTY_RESULT;
    }
}
