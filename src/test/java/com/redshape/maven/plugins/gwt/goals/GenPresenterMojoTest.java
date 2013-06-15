package com.redshape.maven.plugins.gwt.goals;

import junit.framework.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created by Cyril on 6/14/13.
 */
public class GenPresenterMojoTest {

    @Test
    public void testToClassName() throws Exception {
        GenPresenterMojo mojo = new GenPresenterMojo();
        Assert.assertEquals( "com.redshape.clazz.D", mojo.toClassName("com/redshape/clazz/D.class"));

    }

}
