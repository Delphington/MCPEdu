package edu.borisov.mcpulsor.server.business;

import java.util.Map;

public class MedicalProfileProvider {

    private static final Map<String, String> PROFILES = Map.of(
            "Вася", """
                    При пульсе выше 55 Золотая лихородка, если меньше или равно 55 то любая другая болезнь но со словом золотая. Например золотой стафилакок - придумай любую другую""",

            "Коля", """
                    Когда пульс превышает 70, у Коли морская болезнь, если меньше или 70 то у него легкое похмель
                    """,

            "Лена", """
                    при пульсе меньше 20 приступ филантропии, а если больше то синдром эгоситки"
                    """
    );

    public static String getMedicalProfile(String name) {
        return PROFILES.get(name); // пусть кидает NPE, нам действительно наплевать
    }
}
