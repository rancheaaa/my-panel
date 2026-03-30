package com.cq.panel.authlite;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationPerformanceTest {

    private static final class AlwaysContainsSet extends AbstractSet<String> {
        @Override
        public boolean contains(Object o) {
            return o instanceof String;
        }

        @Override
        public Iterator<String> iterator() {
            return Set.<String>of().iterator();
        }

        @Override
        public int size() {
            return 0;
        }
    }

    private static final class Baseline {
        private static final ThreadLocal<AuthenticationToken> TL = new ThreadLocal<>();

        static void set(AuthenticationToken token) {
            TL.set(token);
        }

        static void clear() {
            TL.remove();
        }

        static boolean hasPermission(String permission) {
            AuthenticationToken t = TL.get();
            if (t == null || !t.isAuthenticated()) {
                return false;
            }
            Object principal = t.getPrincipal();
            if (!(principal instanceof User u)) {
                return false;
            }
            return u.getPermissions().contains(permission);
        }
    }

    @Test
    void authorization_10k_iterations_notSlowerThanBaselineByMoreThan20Percent() {
        User user = new User("u", Set.of("ADMIN"), new AlwaysContainsSet());
        AuthorizationService service = new AuthorizationService();

        AuthContext.setCurrentUser(user);
        Baseline.set(new AuthenticationToken(user, "n/a", true));
        try {
            int warm = 20_000;
            String perm = "p";
            int sink = 0;
            for (int i = 0; i < warm; i++) {
                sink += service.hasPermission(perm) ? 1 : 0;
                sink += Baseline.hasPermission(perm) ? 1 : 0;
            }
            assertTrue(sink > 0);

            int iterations = 200_000;
            long authNs = measureMinNs(5, () -> {
                int s = 0;
                for (int i = 0; i < iterations; i++) {
                    s += service.hasPermission(perm) ? 1 : 0;
                }
                if (s == -1) {
                    throw new IllegalStateException();
                }
            });

            long baseNs = measureMinNs(5, () -> {
                int s = 0;
                for (int i = 0; i < iterations; i++) {
                    s += Baseline.hasPermission(perm) ? 1 : 0;
                }
                if (s == -1) {
                    throw new IllegalStateException();
                }
            });

            double ratio = (double) authNs / Math.max(1L, baseNs);
            assertTrue(ratio <= 1.2, "ratio=" + ratio + ", authNs=" + authNs + ", baseNs=" + baseNs);
        } finally {
            AuthContext.clear();
            Baseline.clear();
        }
    }

    private long measureMinNs(int runs, Runnable r) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            r.run();
            long t1 = System.nanoTime();
            best = Math.min(best, t1 - t0);
        }
        return best;
    }
}
