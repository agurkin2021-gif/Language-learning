package ru.spanish.learning;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** One-time A1 family story. Never re-add after deletion or an explicit reset. */
final class GarageCarStory {
    private GarageCarStory() {}

    static final String TITLE = "El autito que nació en el garaje";
    static final String SOURCE = "Сказка про машинку • аргентинский испанский";
    private static final String FLAG = "family_story_garage_car_20261106";

    static final String BODY = """
        Había una vez un garaje pequeño en una calle tranquila.
        Una noche, bajo una luz amarilla, nació un autito azul.
        Tenía cuatro ruedas, dos faros y un corazón muy grande.
        —Hola, mundo —dijo el autito.
        Pero nadie lo escuchó, porque todos dormían.
        Por la mañana, el mecánico abrió la puerta del garaje.
        —¡Qué lindo autito! —dijo.
        El autito quería conocer la ciudad.
        El mecánico revisó las ruedas y llenó el tanque.
        Después abrió la puerta y sonrió.
        —Dale, pequeño, salí a pasear.
        El autito salió despacio y vio árboles, casas y una plaza.
        Cerca de la plaza, una señora esperaba bajo la lluvia.
        —¿Me podés llevar al mercado? —preguntó.
        —¡Claro que sí! —respondió el autito.
        La señora subió y el autito la llevó con cuidado.
        Más tarde, llevó a un maestro hasta la escuela.
        Después llevó a una familia al parque.
        Cada persona tenía un lugar diferente al que llegar.
        El autito aprendió que ayudar a los demás lo hacía feliz.
        Una tarde vio a un abuelo que caminaba muy despacio.
        —¿Adónde vas? —le preguntó el autito.
        —Voy a visitar a mi nieta —respondió el abuelo.
        —Subí, yo te llevo.
        El abuelo llegó a tiempo y abrazó a su nieta.
        Esa noche, el autito volvió al mismo garaje.
        Estaba cansado, pero sus faros brillaban de alegría.
        —Nací en un garaje —pensó—, pero mi camino está con la gente.
        Y desde entonces recorrió la ciudad llevando personas y sonrisas.
        """;

    static final String RUSSIAN = """
        Жил-был маленький гараж на тихой улице.
        Однажды ночью под жёлтой лампочкой в нём родилась синяя машинка.
        У неё было четыре колеса, две фары и очень большое сердце.
        —Привет, мир, — сказала машинка.
        Но никто её не услышал, потому что все спали.
        Утром механик открыл дверь гаража.
        —Какая красивая машинка! — сказал он.
        Машинка хотела познакомиться с городом.
        Механик проверил колёса и заправил бак.
        Потом он открыл дверь и улыбнулся.
        —Давай, малышка, поезжай кататься.
        Машинка медленно выехала и увидела деревья, дома и площадь.
        Возле площади под дождём ждала женщина.
        —Можешь отвезти меня на рынок? — спросила она.
        —Конечно! — ответила машинка.
        Женщина села, и машинка осторожно отвезла её.
        Позже она отвезла учителя в школу.
        Затем она отвезла семью в парк.
        Каждому человеку нужно было добраться в своё место.
        Машинка поняла, что помощь другим делает её счастливой.
        Однажды вечером она увидела дедушку, который шёл очень медленно.
        —Куда ты идёшь? — спросила машинка.
        —Иду навестить внучку, — ответил дедушка.
        —Садись, я тебя отвезу.
        Дедушка приехал вовремя и обнял внучку.
        В ту ночь машинка вернулась в тот же гараж.
        Она устала, но её фары светились от радости.
        —Я родилась в гараже, — подумала она, — но мой путь лежит к людям.
        И с тех пор она ездила по городу, перевозя людей и улыбки.
        """;

    static void seed(SQLiteDatabase db) {
        if (hasFlag(db, FLAG)) return;
        if (!hasFlag(db, "personal_story_reset") && !exists(db)) {
            if (Sentences.split(BODY).size() != Sentences.split(RUSSIAN).size())
                throw new IllegalStateException("Garage car story and translation are not aligned");
            ContentValues story = new ContentValues();
            story.put("title", TITLE);
            story.put("body", BODY.strip());
            story.put("translation", RUSSIAN.strip());
            story.put("source", SOURCE);
            db.insertOrThrow("stories", null, story);
        }
        ContentValues flag = new ContentValues();
        flag.put("key", FLAG);
        flag.put("value", 1);
        db.insertWithOnConflict("app_state", null, flag, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static boolean exists(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM stories WHERE title=? LIMIT 1", new String[]{TITLE})) {
            return c.moveToFirst();
        }
    }

    private static boolean hasFlag(SQLiteDatabase db, String key) {
        try (Cursor c = db.rawQuery("SELECT value FROM app_state WHERE key=?", new String[]{key})) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
}
