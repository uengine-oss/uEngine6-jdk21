package org.uengine.modeling.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by uengine on 2016. 12. 15..
 */
public class CachedResourceManager extends ResourceManager {

    public static final String RESOURCE_MANAGER_CACHE = "resourceManagerCache_";
    /** LRU 캐시 (최대 10개, Jackson 1.x LRUMap 대체) */
    static final Map<String, Object> lruCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Object>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > 10;
            }
        });

    boolean perTransaction;

    public boolean isPerTransaction() {
        return perTransaction;
    }

    public void setPerTransaction(boolean perTransaction) {
        this.perTransaction = perTransaction;
    }

    @Override
    public Object getObject(IResource resource) throws Exception {

        Object cache;
        // if(isPerTransaction()){
        // cache =
        // TransactionContext.getThreadLocalInstance().getSharedContext(RESOURCE_MANAGER_CACHE
        // + resource.getPath());
        // }else{
        cache = lruCache.get(resource.getPath());
        // }

        if (cache != null) {
            return cache;
        }

        Object object = Serializer.deserialize((String) super.getObject(resource));

        // if(isPerTransaction()){
        // TransactionContext.getThreadLocalInstance().setSharedContext(RESOURCE_MANAGER_CACHE
        // + resource.getPath(), object);
        // }else {
        lruCache.put(resource.getPath(), object);
        // }

        return object;
    }

    @Override
    public void save(IResource resource, Object object) throws Exception {
        super.save(resource, object);

        // if(isPerTransaction()) {
        // TransactionContext.getThreadLocalInstance().setSharedContext(RESOURCE_MANAGER_CACHE
        // + resource.getPath(), object);
        // }else{
        lruCache.put(resource.getPath(), object);
        // }
        //

    }
}
