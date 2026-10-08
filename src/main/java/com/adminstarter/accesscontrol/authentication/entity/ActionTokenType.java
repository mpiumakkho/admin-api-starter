package com.adminstarter.accesscontrol.authentication.entity;

/** What an email link lets someone do */
public enum ActionTokenType {
    /** Set a first password and become active */
    INVITE,
    PASSWORD_RESET
}
