package com.reps.demogcloud.services.email;

import java.net.URI;
import java.util.*;

/** One server-configured origin for setup and communication links; no request-supplied redirect. */
public final class FrontendLinks {
    private FrontendLinks() {}
    public static String origin(String value, boolean production) {
        try {
            URI uri=URI.create(value);
            boolean local=Set.of("localhost","127.0.0.1").contains(Objects.toString(uri.getHost(),"")) && !production;
            if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))) throw new IllegalArgumentException();
            return uri.toString().replaceAll("/$","");
        } catch(Exception invalid) { throw new IllegalStateException("REPS frontend public origin is not configured safely"); }
    }
}
