package com.ruoyi.web.test;

import java.util.ArrayList;
import java.util.List;

public class TestAny {
    public static void main(String[] args) {
        List<Integer> l = new ArrayList<Integer>() {
            {
                add(1);
                add(2);
            }
        };
        System.out.println(l.getClass());
    }
}
