package com.orthiva.core.file;

import java.util.UUID;

/** Which aggregate a media asset belongs to (exactly one FK is set on the row). */
public record MediaOwner(Type type, UUID id) {

    public enum Type { ORDER, PLAN, FOLLOW_UP, PERSON }

    public static MediaOwner order(UUID id) {
        return new MediaOwner(Type.ORDER, id);
    }

    public static MediaOwner plan(UUID id) {
        return new MediaOwner(Type.PLAN, id);
    }

    public static MediaOwner followUp(UUID id) {
        return new MediaOwner(Type.FOLLOW_UP, id);
    }

    public static MediaOwner person(UUID id) {
        return new MediaOwner(Type.PERSON, id);
    }
}
