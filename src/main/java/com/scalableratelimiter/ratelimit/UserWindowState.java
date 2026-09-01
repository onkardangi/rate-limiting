package com.scalableratelimiter.ratelimit;

record UserWindowState(long windowMinute, int count) {
}
