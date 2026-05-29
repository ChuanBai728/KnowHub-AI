package com.baidu.fsg.uid;

import com.baidu.fsg.uid.exception.UidGenerateException;

/**
 * Baidu UID generator contract used by KnowHub AI.
 */
public interface UidGenerator {

    long getUid() throws UidGenerateException;

    String parseUid(long uid);
}
