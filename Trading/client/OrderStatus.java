/* Copyright (C) 2013 Interactive Brokers LLC. All rights reserved.  This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */
package client;

public enum OrderStatus {
    ApiPending,
    ApiCancelled,
    PendingCancel,
    DeadlineCancelled,
    Cancelled,
    PreSubmitted,
    Submitted,
    Filled,
    Inactive,
    PendingSubmit,
    Unknown,
    NoOrder,
    Created,
    ConstructedInHandler;

    public static OrderStatus get(String apiString) {
        for (OrderStatus type : values()) {
            if (type.name().equalsIgnoreCase(apiString)) {
                return type;
            }
        }
        return Unknown;
    }

    public boolean isActive() {
        return this == PreSubmitted || this == PendingCancel || this == Submitted || this == PendingSubmit;
    }
}
