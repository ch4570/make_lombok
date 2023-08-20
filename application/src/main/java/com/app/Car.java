package com.app;

import com.rex.annotation.AccessLevel;
import com.rex.annotation.Getter;
import com.rex.annotation.NoArgsConstructor;


@Getter
//@Setter
@NoArgsConstructor(accessLevel = AccessLevel.PRIVATE)
public class Car {

    private String name;
    private int price;

    private Car(String name) {
        this.name = name;
    }

    public Car(int price) {
        this.price = price;
    }

    protected Car(String name, int price) {
        this.name = name;
        this.price = price;
    }
}


