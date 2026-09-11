package com.example.bank;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountTest {
    @Test
    void balanceIsStored() {
        assertEquals(10.0, new Account("a", 10.0).getBalance());
    }
}
