package org.uengine.kernel.test;

import junit.framework.TestCase;
import org.uengine.kernel.HumanActivity;

public class ReceiveActivityTest extends TestCase {

    public void testGetMappingInValuesWithoutEventSynchronizationReturnsEmpty() throws Exception {
        HumanActivity activity = new HumanActivity();

        assertTrue(activity.getMappingInValues(null).isEmpty());
    }
}
