package io.ratemaster.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratemaster")
public class RateMasterProperties {

    private final Redis redis = new Redis();
    private final Executor executor = new Executor();

    public Redis getRedis() {
        return redis;
    }

    public Executor getExecutor() {
        return executor;
    }

    public static class Redis {
        /**
         * The maximum time (in milliseconds) to wait for a rate limit evaluation 
         * before falling back. Default is 500ms.
         */
        private long commandTimeoutMs = 500;

        public long getCommandTimeoutMs() {
            return commandTimeoutMs;
        }

        public void setCommandTimeoutMs(long commandTimeoutMs) {
            this.commandTimeoutMs = commandTimeoutMs;
        }
    }

    public static class Executor {
        /**
         * Core pool size for the platform thread pool.
         * Default is 10.
         */
        private int coreSize = 10;

        /**
         * Maximum pool size for the platform thread pool.
         * Default is 50.
         */
        private int maxSize = 50;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
