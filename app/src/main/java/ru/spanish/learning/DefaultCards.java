package ru.spanish.learning;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

public final class DefaultCards {
    private DefaultCards() {}
    private static final String[] VERBS = lines("""
ser|быть
estar|быть, находиться
tener|иметь
hacer|делать
poder|мочь
decir|говорить, сказать
ir|идти, ехать
ver|видеть
dar|давать
saber|знать
querer|хотеть, любить
llegar|прибывать
pasar|проходить, проводить
deber|быть должным
poner|класть, ставить
parecer|казаться
quedar|оставаться
creer|верить, считать
hablar|говорить
llevar|нести, носить
dejar|оставлять, позволять
seguir|следовать, продолжать
encontrar|находить
llamar|звать, звонить
venir|приходить, приезжать
pensar|думать
salir|выходить
volver|возвращаться
tomar|брать, пить
conocer|знать, быть знакомым
vivir|жить
sentir|чувствовать
tratar|пытаться, обращаться
mirar|смотреть
contar|считать, рассказывать
empezar|начинать
esperar|ждать, надеяться
buscar|искать
existir|существовать
entrar|входить
trabajar|работать
escribir|писать
perder|терять
producir|производить
ocurrir|происходить
entender|понимать
pedir|просить, заказывать
recibir|получать
recordar|помнить
terminar|заканчивать
permitir|позволять
aparecer|появляться
conseguir|получать, добиваться
comenzar|начинать
servir|служить, подавать
sacar|доставать, вынимать
necesitar|нуждаться
mantener|поддерживать
resultar|оказываться
leer|читать
caer|падать
cambiar|менять
presentar|представлять
crear|создавать
abrir|открывать
considerar|считать, рассматривать
oír|слышать
acabar|заканчивать
convertir|превращать
ganar|выигрывать, зарабатывать
formar|формировать
traer|приносить
partir|уходить, делить
morir|умирать
aceptar|принимать, соглашаться
realizar|осуществлять
suponer|предполагать
comprender|понимать
lograr|достигать
explicar|объяснять
preguntar|спрашивать
tocar|трогать, играть
reconocer|узнавать, признавать
estudiar|учиться, изучать
alcanzar|достигать
nacer|рождаться
dirigir|руководить, направлять
correr|бежать
utilizar|использовать
pagar|платить
ayudar|помогать
gustar|нравиться
jugar|играть
escuchar|слушать
cumplir|выполнять, исполнять
ofrecer|предлагать
descubrir|обнаруживать
levantar|поднимать
intentar|пытаться
usar|использовать
""");
    private static final String[] NOUNS = lines("""
tiempo|время
año|год
día|день
vez|раз
cosa|вещь
hombre|мужчина
mujer|женщина
vida|жизнь
mano|рука
parte|часть
mundo|мир
caso|случай
país|страна
lugar|место
trabajo|работа
persona|человек
casa|дом
agua|вода
padre|отец
madre|мать
hijo|сын
hija|дочь
amigo|друг
familia|семья
ciudad|город
calle|улица
coche|машина
puerta|дверь
mesa|стол
silla|стул
comida|еда
café|кофе
leche|молоко
pan|хлеб
carne|мясо
pescado|рыба
fruta|фрукт
verdura|овощ
dinero|деньги
precio|цена
tienda|магазин
mercado|рынок
escuela|школа
libro|книга
palabra|слово
pregunta|вопрос
respuesta|ответ
problema|проблема
idea|идея
historia|история
noche|ночь
mañana|утро
tarde|вечер, вторая половина дня
semana|неделя
mes|месяц
hora|час
minuto|минута
momento|момент
cabeza|голова
cara|лицо
ojo|глаз
boca|рот
pie|ступня
corazón|сердце
cuerpo|тело
nombre|имя
número|номер, число
forma|форма, способ
fin|конец
principio|начало
lado|сторона
camino|дорога, путь
puente|мост
plaza|площадь
parque|парк
playa|пляж
río|река
mar|море
montaña|гора
cielo|небо
sol|солнце
lluvia|дождь
viento|ветер
fuego|огонь
aire|воздух
ropa|одежда
zapato|обувь, туфля
camisa|рубашка
pantalón|брюки
teléfono|телефон
computadora|компьютер
internet|интернет
música|музыка
película|фильм
foto|фотография
viaje|путешествие
hotel|отель
restaurante|ресторан
tren|поезд
avión|самолёт
""");
    private static final String[] ADJECTIVES = lines("""
bueno|хороший
malo|плохой
grande|большой
pequeño|маленький
nuevo|новый
viejo|старый
joven|молодой
importante|важный
diferente|другой, различный
mismo|тот же самый
primero|первый
último|последний
mejor|лучший
peor|худший
alto|высокий
bajo|низкий
largo|длинный
corto|короткий
fácil|лёгкий
difícil|трудный
rápido|быстрый
lento|медленный
fuerte|сильный
débil|слабый
feliz|счастливый
triste|грустный
claro|ясный, светлый
oscuro|тёмный
blanco|белый
negro|чёрный
rojo|красный
verde|зелёный
azul|синий
amarillo|жёлтый
caliente|горячий
frío|холодный
limpio|чистый
sucio|грязный
lleno|полный
vacío|пустой
abierto|открытый
cerrado|закрытый
caro|дорогой
barato|дешёвый
rico|богатый, вкусный
pobre|бедный
bonito|красивый
feo|некрасивый
amable|добрый, любезный
serio|серьёзный
tranquilo|спокойный
nervioso|нервный
cansado|уставший
ocupado|занятый
libre|свободный
listo|готовый, сообразительный
seguro|безопасный, уверенный
peligroso|опасный
posible|возможный
imposible|невозможный
necesario|необходимый
útil|полезный
inútil|бесполезный
correcto|правильный
equivocado|ошибочный
verdadero|истинный
falso|ложный
normal|нормальный
especial|особенный
general|общий
principal|главный
personal|личный
público|общественный
privado|частный
local|местный
nacional|национальный
internacional|международный
natural|естественный
real|реальный
simple|простой
completo|полный
común|обычный, общий
raro|редкий, странный
igual|одинаковый
distinto|отличающийся
cercano|близкий
lejano|далёкий
temprano|ранний
tardío|поздний
dulce|сладкий
salado|солёный
amargo|горький
suave|мягкий
duro|твёрдый, тяжёлый
seco|сухой
húmedo|влажный
ancho|широкий
estrecho|узкий
pesado|тяжёлый
ligero|лёгкий
""");
    private static final String[] EXPRESSIONS = lines("""
tener ganas de|хотеть, иметь желание
darse cuenta de|осознавать, замечать
tener en cuenta|принимать во внимание
por supuesto|конечно
de acuerdo|согласен, договорились
no pasa nada|ничего страшного
qué onda|как дела, что происходит
más o menos|более или менее
por ahora|пока что
a veces|иногда
de vez en cuando|время от времени
todo el tiempo|всё время
al final|в конце концов
en serio|серьёзно
la verdad es que|правда в том, что
por ejemplo|например
es decir|то есть
o sea|то есть
sin embargo|однако
además de|кроме того
a pesar de|несмотря на
en vez de|вместо
por eso|поэтому
así que|так что
ya está|всё, готово
ya fue|ладно, проехали
ni idea|без понятия
me parece que|мне кажется, что
creo que|я думаю, что
supongo que|я полагаю, что
estoy seguro de|я уверен в
no estoy seguro|я не уверен
qué lástima|как жаль
qué suerte|как повезло
tengo que|мне нужно, я должен
hay que|нужно, следует
acabar de|только что сделать
volver a|снова сделать
dejar de|перестать делать
seguir + gerundio|продолжать делать
estar por|собираться, быть на грани
estar de acuerdo|быть согласным
tener razón|быть правым
no tener razón|быть неправым
echar de menos|скучать по
hacer falta|быть нужным, не хватать
dar igual|быть безразличным
qué tal|как дела, как
para siempre|навсегда
salir para|отправляться в, выходить к
""");

    static void seed(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            seedGroup(db, VERBS, 0, 1);
            seedPairs(db, DefaultVerbsExtra.DATA, 0, 1);
            seedGroup(db, NOUNS, 0, 2);
            seedPairs(db, DefaultNounsExtra.DATA, 0, 2);
            seedGroup(db, ADJECTIVES, 0, 3);
            seedPairs(db, DefaultAdjectivesExtra.DATA, 0, 3);
            seedGroup(db, EXPRESSIONS, 1, 0);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void seedGroup(SQLiteDatabase db, String[] rows, int kind, int category) {
        for (String row : rows) {
            int p = row.indexOf('|');
            if (p <= 0) continue;
            String es = row.substring(0, p), ru = row.substring(p + 1);
            android.database.Cursor c = db.rawQuery("SELECT 1 FROM cards WHERE spanish=? AND meaning=? LIMIT 1", new String[]{es, ru});
            boolean exists;
            try { exists = c.moveToFirst(); } finally { c.close(); }
            if (exists) continue;
            ContentValues v = new ContentValues();
            v.put("spanish", es); v.put("meaning", ru); v.put("kind", kind); v.put("category", category); v.put("mastered", 0);
            db.insertOrThrow("cards", null, v);
        }
    }

    private static void seedPairs(SQLiteDatabase db, String[][] rows, int kind, int category) {
        for (String[] row : rows) {
            if (row.length != 2) continue;
            String es = row[0], ru = row[1];
            android.database.Cursor c = db.rawQuery("SELECT 1 FROM cards WHERE spanish=? AND meaning=? LIMIT 1", new String[]{es, ru});
            boolean exists;
            try { exists = c.moveToFirst(); } finally { c.close(); }
            if (exists) continue;
            ContentValues v = new ContentValues();
            v.put("spanish", es); v.put("meaning", ru); v.put("kind", kind); v.put("category", category); v.put("mastered", 0);
            db.insertOrThrow("cards", null, v);
        }
    }

    private static String[] lines(String block) {
        return block.strip().split("\\R");
    }
}
