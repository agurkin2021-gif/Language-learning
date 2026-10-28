package ru.spanish.learning;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Curated 700 beginner A0-A1 ES words with verified RU translations.
 * 183 verbs, 406 nouns, 111 adjectives. Includes regional Argentine Spanish.
 * Once-only Mama/Dima starter pack. Never replaces user cards or Papa's DB.
 */
final class BeginnerCards {
    private BeginnerCards() {}

    private static final String ROWS = """
        ser|быть|1
        estar|быть, находиться|1
        tener|иметь|1
        hacer|делать|1
        poder|мочь|1
        decir|говорить, сказать|1
        ir|идти, ехать|1
        ver|видеть|1
        dar|давать|1
        saber|знать|1
        querer|хотеть, любить|1
        llegar|прибывать|1
        pasar|проходить, проводить|1
        deber|быть должным|1
        poner|класть, ставить|1
        parecer|казаться|1
        quedar|оставаться|1
        creer|верить, считать|1
        hablar|говорить|1
        llevar|нести, носить|1
        dejar|оставлять, позволять|1
        seguir|следовать, продолжать|1
        encontrar|находить|1
        llamar|звать, звонить|1
        venir|приходить, приезжать|1
        pensar|думать|1
        salir|выходить|1
        volver|возвращаться|1
        tomar|брать, пить|1
        conocer|знать, быть знакомым|1
        vivir|жить|1
        sentir|чувствовать|1
        mirar|смотреть|1
        contar|считать, рассказывать|1
        empezar|начинать|1
        esperar|ждать, надеяться|1
        buscar|искать|1
        entrar|входить|1
        trabajar|работать|1
        escribir|писать|1
        perder|терять|1
        entender|понимать|1
        pedir|просить, заказывать|1
        recibir|получать|1
        recordar|помнить|1
        terminar|заканчивать|1
        servir|служить, подавать|1
        sacar|доставать, вынимать|1
        necesitar|нуждаться|1
        leer|читать|1
        caer|падать|1
        cambiar|менять|1
        abrir|открывать|1
        oír|слышать|1
        acabar|заканчивать|1
        ganar|выигрывать, зарабатывать|1
        traer|приносить|1
        morir|умирать|1
        aceptar|принимать, соглашаться|1
        explicar|объяснять|1
        preguntar|спрашивать|1
        estudiar|учиться, изучать|1
        correr|бежать|1
        pagar|платить|1
        ayudar|помогать|1
        gustar|нравиться|1
        jugar|играть|1
        escuchar|слушать|1
        usar|использовать|1
        andar|ходить|1
        amar|любить|1
        aprender|учиться|1
        bailar|танцевать|1
        beber|пить|1
        cerrar|закрывать|1
        cocinar|готовить|1
        comer|есть|1
        comprar|покупать|1
        conducir|водить|1
        cortar|резать|1
        dormir|спать|1
        elegir|выбирать|1
        enseñar|обучать, показывать|1
        enviar|отправлять|1
        faltar|не хватать, отсутствовать|1
        guardar|хранить, сохранять|1
        lavar|мыть|1
        limpiar|чистить|1
        mandar|посылать, приказывать|1
        mover|двигать|1
        nadar|плавать|1
        olvidar|забывать|1
        parar|останавливаться|1
        preparar|готовить|1
        quitar|убирать, снимать|1
        romper|ломать|1
        sentarse|садиться|1
        subir|подниматься|1
        bajar|спускаться|1
        viajar|путешествовать|1
        visitar|посещать|1
        vender|продавать|1
        alquilar|арендовать|1
        arreglar|чинить, улаживать|1
        apagar|выключать|1
        encender|включать|1
        cargar|заряжать, загружать|1
        descargar|скачивать, разгружать|1
        borrar|удалять, стирать|1
        copiar|копировать|1
        reservar|бронировать|1
        cancelar|отменять|1
        confirmar|подтверждать|1
        gastar|тратить|1
        ahorrar|экономить|1
        prestar|одалживать|1
        devolver|возвращать|1
        cuidar|заботиться|1
        doler|болеть|1
        mejorar|улучшать|1
        descansar|отдыхать|1
        despertar|будить, просыпаться|1
        levantarse|вставать|1
        acostarse|ложиться|1
        ducharse|принимать душ|1
        vestirse|одеваться|1
        peinarse|причёсываться|1
        reír|смеяться|1
        llorar|плакать|1
        gritar|кричать|1
        besar|целовать|1
        abrazar|обнимать|1
        casarse|жениться, выходить замуж|1
        crecer|расти|1
        preferir|предпочитать|1
        soñar|мечтать|1
        imaginar|представлять|1
        acordarse|вспоминать|1
        notar|замечать|1
        mostrar|показывать|1
        comparar|сравнивать|1
        probar|пробовать|1
        repetir|повторять|1
        practicar|практиковать|1
        contestar|отвечать|1
        responder|отвечать|1
        significar|значить|1
        avisar|предупреждать|1
        compartir|делиться|1
        encontrarse|встречаться, находиться|1
        reunirse|встречаться, собираться|1
        acompañar|сопровождать|1
        invitar|приглашать|1
        celebrar|праздновать|1
        regalar|дарить|1
        agradecer|благодарить|1
        perdonar|прощать|1
        disculparse|извиняться|1
        molestar|беспокоить|1
        preocupar|беспокоить|1
        preocuparse|беспокоиться|1
        sorprender|удивлять|1
        asustar|пугать|1
        aburrir|надоедать|1
        divertir|развлекать|1
        disfrutar|наслаждаться|1
        interesar|интересовать|1
        importar|иметь значение|1
        funcionar|работать, функционировать|1
        reparar|ремонтировать|1
        construir|строить|1
        dibujar|рисовать|1
        pintar|красить, рисовать|1
        cantar|петь|1
        grabar|записывать|1
        fotografiar|фотографировать|1
        cruzar|пересекать|1
        girar|поворачивать|1
        doblar|поворачивать, сгибать|1
        caminar|ходить пешком|1
        saltar|прыгать|1
        subirse|садиться, забираться|1
        bajarse|выходить, спускаться|1
        tiempo|время|2
        año|год|2
        día|день|2
        vez|раз|2
        cosa|вещь|2
        hombre|мужчина|2
        mujer|женщина|2
        vida|жизнь|2
        mano|рука|2
        mundo|мир|2
        país|страна|2
        lugar|место|2
        trabajo|работа|2
        persona|человек|2
        casa|дом|2
        agua|вода|2
        padre|отец|2
        madre|мать|2
        hijo|сын|2
        hija|дочь|2
        amigo|друг|2
        familia|семья|2
        ciudad|город|2
        calle|улица|2
        puerta|дверь|2
        mesa|стол|2
        silla|стул|2
        comida|еда|2
        café|кофе|2
        leche|молоко|2
        pan|хлеб|2
        carne|мясо|2
        pescado|рыба|2
        fruta|фрукт|2
        verdura|овощ|2
        dinero|деньги|2
        precio|цена|2
        tienda|магазин|2
        mercado|рынок|2
        escuela|школа|2
        libro|книга|2
        palabra|слово|2
        pregunta|вопрос|2
        respuesta|ответ|2
        problema|проблема|2
        idea|идея|2
        historia|история|2
        noche|ночь|2
        mañana|утро|2
        tarde|вечер, вторая половина дня|2
        semana|неделя|2
        mes|месяц|2
        hora|час|2
        minuto|минута|2
        momento|момент|2
        cabeza|голова|2
        cara|лицо|2
        ojo|глаз|2
        boca|рот|2
        pie|ступня|2
        corazón|сердце|2
        cuerpo|тело|2
        nombre|имя|2
        número|номер, число|2
        lado|сторона|2
        camino|дорога, путь|2
        puente|мост|2
        plaza|площадь|2
        parque|парк|2
        playa|пляж|2
        río|река|2
        mar|море|2
        montaña|гора|2
        cielo|небо|2
        sol|солнце|2
        lluvia|дождь|2
        viento|ветер|2
        aire|воздух|2
        ropa|одежда|2
        zapato|обувь, туфля|2
        camisa|рубашка|2
        pantalón|брюки|2
        teléfono|телефон|2
        computadora|компьютер|2
        internet|интернет|2
        música|музыка|2
        película|фильм|2
        foto|фотография|2
        viaje|путешествие|2
        hotel|отель|2
        restaurante|ресторан|2
        tren|поезд|2
        avión|самолёт|2
        perro|собака|2
        gato|кот|2
        animal|животное|2
        pájaro|птица|2
        caballo|лошадь|2
        vaca|корова|2
        árbol|дерево|2
        flor|цветок|2
        bosque|лес|2
        campo|поле|2
        jardín|сад|2
        tierra|земля|2
        nube|облако|2
        estación|станция, сезон|2
        primavera|весна|2
        verano|лето|2
        otoño|осень|2
        invierno|зима|2
        lunes|понедельник|2
        martes|вторник|2
        miércoles|среда|2
        jueves|четверг|2
        viernes|пятница|2
        sábado|суббота|2
        domingo|воскресенье|2
        enero|январь|2
        febrero|февраль|2
        marzo|март|2
        abril|апрель|2
        mayo|май|2
        junio|июнь|2
        julio|июль|2
        agosto|август|2
        septiembre|сентябрь|2
        octubre|октябрь|2
        noviembre|ноябрь|2
        diciembre|декабрь|2
        desayuno|завтрак|2
        almuerzo|обед|2
        cena|ужин|2
        bebida|напиток|2
        jugo|сок|2
        té|чай|2
        azúcar|сахар|2
        sal|соль|2
        queso|сыр|2
        huevo|яйцо|2
        arroz|рис|2
        pasta|макароны|2
        sopa|суп|2
        ensalada|салат|2
        pollo|курица|2
        cerdo|свинина|2
        tomate|помидор|2
        papa|картофель|2
        cebolla|лук|2
        ajo|чеснок|2
        manzana|яблоко|2
        banana|банан|2
        naranja|апельсин|2
        limón|лимон|2
        botella|бутылка|2
        vaso|стакан|2
        taza|чашка|2
        plato|тарелка|2
        cuchillo|нож|2
        tenedor|вилка|2
        cuchara|ложка|2
        cocina|кухня|2
        baño|ванная|2
        habitación|комната|2
        dormitorio|спальня|2
        cama|кровать|2
        sofá|диван|2
        ventana|окно|2
        pared|стена|2
        techo|потолок|2
        piso|пол, этаж|2
        llave|ключ|2
        luz|свет|2
        electricidad|электричество|2
        gas|газ|2
        calefacción|отопление|2
        heladera|холодильник|2
        horno|духовка|2
        microondas|микроволновка|2
        lavadora|стиральная машина|2
        toalla|полотенце|2
        jabón|мыло|2
        champú|шампунь|2
        cepillo|щётка|2
        espejo|зеркало|2
        reloj|часы|2
        bolsa|сумка, пакет|2
        mochila|рюкзак|2
        billetera|кошелёк|2
        documento|документ|2
        tarjeta|карта|2
        banco|банк|2
        cliente|клиент|2
        tarea|задача|2
        mensaje|сообщение|2
        llamada|звонок|2
        dirección|адрес, направление|2
        esquina|угол|2
        avenida|проспект|2
        barrio|район|2
        departamento|квартира|2
        ascensor|лифт|2
        escalera|лестница|2
        aeropuerto|аэропорт|2
        parada|остановка|2
        colectivo|автобус|2
        subte|метро|2
        taxi|такси|2
        bicicleta|велосипед|2
        moto|мотоцикл|2
        barco|корабль|2
        boleto|билет|2
        valija|чемодан|2
        médico|врач|2
        hospital|больница|2
        farmacia|аптека|2
        medicina|лекарство|2
        dolor|боль|2
        salud|здоровье|2
        fiebre|температура, жар|2
        diente|зуб|2
        espalda|спина|2
        brazo|рука|2
        pierna|нога|2
        dedo|палец|2
        nariz|нос|2
        oreja|ухо|2
        cuello|шея|2
        piel|кожа|2
        pelo|волосы|2
        campera|куртка|2
        vestido|платье|2
        falda|юбка|2
        gorra|кепка|2
        media|носок|2
        talle|размер одежды|2
        color|цвет|2
        tamaño|размер|2
        peso|вес|2
        edad|возраст|2
        cumpleaños|день рождения|2
        niño|мальчик|2
        niña|девочка|2
        chico|парень, мальчик|2
        chica|девушка, девочка|2
        bebé|младенец|2
        hermano|брат|2
        hermana|сестра|2
        abuelo|дедушка|2
        abuela|бабушка|2
        tío|дядя|2
        tía|тётя|2
        primo|двоюродный брат|2
        prima|двоюродная сестра|2
        esposa|жена|2
        marido|муж|2
        pareja|пара, партнёр|2
        vecino|сосед|2
        vecina|соседка|2
        profesor|учитель, преподаватель|2
        profesora|учительница, преподавательница|2
        estudiante|ученик, студент|2
        señor|господин, мужчина|2
        señora|госпожа, женщина|2
        doctor|врач|2
        doctora|врач|2
        dentista|стоматолог|2
        policía|полиция, полицейский|2
        mamá|мама|2
        papá|папа|2
        panadería|булочная|2
        verdulería|овощной магазин|2
        carnicería|мясной магазин|2
        kiosco|киоск|2
        supermercado|супермаркет|2
        iglesia|церковь|2
        museo|музей|2
        biblioteca|библиотека|2
        gimnasio|спортзал|2
        cine|кинотеатр, кино|2
        bar|бар|2
        cafetería|кафе|2
        peluquería|парикмахерская|2
        centro|центр|2
        patio|двор|2
        clase|урок, класс|2
        curso|курс|2
        examen|экзамен|2
        nota|оценка, заметка|2
        papel|бумага|2
        cuaderno|тетрадь|2
        lápiz|карандаш|2
        lapicera|ручка|2
        goma|ластик|2
        regla|линейка|2
        pizarrón|школьная доска|2
        aula|классная комната|2
        recreo|перемена|2
        carpeta|папка|2
        televisión|телевидение|2
        radio|радио|2
        celular|мобильный телефон|2
        pantalla|экран|2
        teclado|клавиатура|2
        cargador|зарядное устройство|2
        auricular|наушник|2
        cámara|камера|2
        enchufe|розетка, вилка|2
        cable|кабель|2
        batería|батарея, аккумулятор|2
        comedor|столовая|2
        sillón|кресло|2
        armario|шкаф|2
        cajón|ящик|2
        cortina|штора|2
        almohada|подушка|2
        sábana|простыня|2
        manta|одеяло|2
        colchón|матрас|2
        escritorio|письменный стол|2
        lámpara|лампа|2
        alfombra|ковёр|2
        mueble|мебель, предмет мебели|2
        detergente|моющее средство|2
        escoba|метла|2
        balde|ведро|2
        basura|мусор|2
        ducha|душ|2
        inodoro|унитаз|2
        canilla|водопроводный кран|2
        pileta|раковина, бассейн|2
        olla|кастрюля|2
        sartén|сковорода|2
        pava|чайник|2
        jarra|кувшин|2
        caja|коробка|2
        paquete|пакет, посылка|2
        servilleta|салфетка|2
        mantel|скатерть|2
        mate|мате|2
        yerba|йерба для мате|2
        manteca|сливочное масло|2
        tostada|тост|2
        galletita|печенье|2
        mermelada|джем|2
        miel|мёд|2
        helado|мороженое|2
        yogur|йогурт|2
        harina|мука|2
        aceite|масло|2
        vinagre|уксус|2
        pimienta|перец|2
        pera|груша|2
        frutilla|клубника|2
        uva|виноград|2
        sandía|арбуз|2
        melón|дыня|2
        durazno|персик|2
        palta|авокадо|2
        zanahoria|морковь|2
        lechuga|салат-латук|2
        pepino|огурец|2
        zapallo|тыква|2
        choclo|кукуруза|2
        maní|арахис|2
        nuez|орех|2
        remera|футболка|2
        buzo|толстовка|2
        abrigo|пальто, тёплая одежда|2
        zapatilla|кроссовка|2
        sandalia|сандалия|2
        bota|сапог|2
        short|шорты|2
        pollera|юбка|2
        sombrero|шляпа|2
        bufanda|шарф|2
        guante|перчатка|2
        cinturón|ремень|2
        anteojos|очки|2
        anillo|кольцо|2
        luna|луна|2
        estrella|звезда|2
        hoja|лист|2
        rama|ветка|2
        pez|рыба|2
        oveja|овца|2
        pato|утка|2
        gallina|курица|2
        conejo|кролик|2
        ratón|мышь|2
        oso|медведь|2
        león|лев|2
        mariposa|бабочка|2
        mosquito|комар|2
        abeja|пчела|2
        hormiga|муравей|2
        rodilla|колено|2
        hombro|плечо|2
        pecho|грудь|2
        panza|живот|2
        muñeca|запястье|2
        garganta|горло|2
        lengua|язык|2
        labio|губа|2
        ceja|бровь|2
        auto|машина|2
        bueno|хороший|3
        malo|плохой|3
        grande|большой|3
        pequeño|маленький|3
        nuevo|новый|3
        viejo|старый|3
        joven|молодой|3
        importante|важный|3
        diferente|другой, различный|3
        primero|первый|3
        último|последний|3
        mejor|лучший|3
        alto|высокий|3
        bajo|низкий|3
        largo|длинный|3
        corto|короткий|3
        fácil|лёгкий|3
        difícil|трудный|3
        rápido|быстрый|3
        lento|медленный|3
        fuerte|сильный|3
        feliz|счастливый|3
        triste|грустный|3
        claro|ясный, светлый|3
        blanco|белый|3
        negro|чёрный|3
        rojo|красный|3
        verde|зелёный|3
        azul|синий|3
        amarillo|жёлтый|3
        caliente|горячий|3
        frío|холодный|3
        limpio|чистый|3
        sucio|грязный|3
        lleno|полный|3
        vacío|пустой|3
        abierto|открытый|3
        cerrado|закрытый|3
        caro|дорогой|3
        barato|дешёвый|3
        rico|богатый, вкусный|3
        feo|некрасивый|3
        tranquilo|спокойный|3
        nervioso|нервный|3
        cansado|уставший|3
        ocupado|занятый|3
        libre|свободный|3
        listo|готовый, сообразительный|3
        seguro|безопасный, уверенный|3
        posible|возможный|3
        normal|нормальный|3
        simple|простой|3
        igual|одинаковый|3
        cercano|близкий|3
        lejano|далёкий|3
        dulce|сладкий|3
        salado|солёный|3
        duro|твёрдый, тяжёлый|3
        seco|сухой|3
        ancho|широкий|3
        estrecho|узкий|3
        contento|довольный|3
        enojado|злой|3
        preocupado|обеспокоенный|3
        sorprendido|удивлённый|3
        asustado|испуганный|3
        aburrido|скучающий, скучный|3
        divertido|весёлый|3
        interesante|интересный|3
        cómodo|удобный|3
        incómodo|неудобный|3
        fresco|свежий, прохладный|3
        tibio|тёплый|3
        mojado|мокрый|3
        delgado|худой, тонкий|3
        gordo|толстый|3
        flaco|худой|3
        hermoso|красивый|3
        lindo|милый, красивый|3
        horrible|ужасный|3
        perfecto|идеальный|3
        famoso|известный|3
        moderno|современный|3
        próximo|ближайший, следующий|3
        anterior|предыдущий|3
        medio|средний|3
        suficiente|достаточный|3
        demasiado|чрезмерный|3
        redondo|круглый|3
        izquierdo|левый|3
        derecho|правый|3
        sano|здоровый|3
        enfermo|больной|3
        despierto|бодрствующий|3
        dormido|спящий|3
        hambriento|голодный|3
        sabroso|вкусный|3
        picante|острый|3
        usado|подержанный|3
        roto|сломанный|3
        disponible|доступный|3
        gratis|бесплатный|3
        inteligente|умный|3
        simpático|приятный, симпатичный|3
        ruidoso|шумный|3
        silencioso|тихий|3
        alegre|весёлый|3
        solo|одинокий, один|3
        adulto|взрослый|3
        mayor|старший, больший|3
        menor|младший, меньший|3
        """;

    static int seed(SQLiteDatabase db) {
        Set<String> present = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT spanish, kind FROM cards", null)) {
            while (c.moveToNext())
                present.add(c.getInt(1) + ":" + c.getString(0).trim().toLowerCase(Locale.ROOT));
        }
        int inserted = 0;
        db.beginTransaction();
        try {
            for (String line : ROWS.strip().split("\\R")) {
                String[] parts = line.strip().split("\\|", 3);
                if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank())
                    throw new IllegalStateException("Invalid beginner vocabulary record");
                int category = Integer.parseInt(parts[2]);
                if (category < 1 || category > 3)
                    throw new IllegalStateException("Invalid beginner category");
                String key = "0:" + parts[0].trim().toLowerCase(Locale.ROOT);
                if (!present.add(key)) continue;
                ContentValues values = new ContentValues();
                values.put("spanish", parts[0]);
                values.put("meaning", parts[1]);
                values.put("kind", 0);
                values.put("category", category);
                values.put("mastered", 0);
                db.insertOrThrow("cards", null, values);
                inserted++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    static int size() { return ROWS.strip().split("\\R").length; }
}
