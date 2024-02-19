package com.harsh.profit.fitness;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ReportGenerator {
    Handler mHandler = new Handler();
    long total;

    TreeMap<Long,List> map = new TreeMap<>();

    Table table;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void generatePdf(Context context){
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        CollectionReference ref = db.collection("users").document(user.getUid())
                .collection("Days");


        float[] columnWidth = {100f,200f,200f,200f,200f,100f};
        table = new Table(columnWidth);
        table.addCell("Sr no.");
        table.addCell("Date");
        table.addCell("Steps");
        table.addCell("Calories");
        table.addCell("Distance");
        table.addCell("Move Minutes");
        ref.document("Total").get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {


                List<List> list = new ArrayList<>();
                total = (long)documentSnapshot.get("Total");
                long i;
                if(total<=30){
                    i =(long) 1;
                    while(i <= total){
                        long finalI = i;
                        ref.document(i+"").get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                List<Object> data = new ArrayList<>();
                                data.add(documentSnapshot.get("Date"));
                                data.add(documentSnapshot.get("Steps"));
                                data.add(documentSnapshot.get("Calories"));
                                data.add(documentSnapshot.get("Distance"));
                                data.add(documentSnapshot.get("Move Minutes"));
                                list.add(0,data);
                                map.put(finalI,list.get(0));
                            }
                        });
                        i++;
                    }
                }
                else{
                    i = total-30;
                    while(i<=total){
                        long finalI = i;
                        ref.document(String.valueOf(i)).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                Log.i("Table", finalI +" day");
                                Log.i("Steps", documentSnapshot.get("Steps")+"");
                                Log.i("Calories", documentSnapshot.get("Calories")+"");
                                Log.i("Move Minutes", documentSnapshot.get("Move Minutes")+"");
                                Log.i("Distance", documentSnapshot.get("Distance")+"");
                                Log.i("Number of Rows = ",table.getNumberOfRows()+"");


                                List<Object> data = new ArrayList<>();
                                data.add(documentSnapshot.get("Date"));
                                data.add(documentSnapshot.get("Steps"));
                                data.add(documentSnapshot.get("Calories"));
                                data.add(documentSnapshot.get("Distance"));
                                data.add(documentSnapshot.get("Move Minutes"));
                                Log.i("List is",data.toString());
                                list.add(0,data);
                                map.put(finalI,list.get(0));
                            }
                        });
                        i++;
                    }
                }

                mHandler.postDelayed(makeTable,2000);

                mHandler.postDelayed(generate,4000);

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i("Table","Unsuccessful");
            }
        });
        Toast.makeText(context,"Report Downloaded",Toast.LENGTH_SHORT).show();
    }


    private final Runnable makeTable = new Runnable() {
        @Override
        public void run() {
            long i;
            if(total<=30){
                i = 1;
            }
            else{
                i = total-30;
            }
            while(i<=total){
                table.addCell(i+"");
                table.addCell(map.get(i).get(0).toString());
                table.addCell(map.get(i).get(1).toString());
                table.addCell(map.get(i).get(2).toString());
                table.addCell(map.get(i).get(3).toString());
                table.addCell(map.get(i).get(4).toString());
                i++;
            }
        }
    };

    private final Runnable generate = new Runnable() {
        @Override
        public void run() {
            Log.i("Map table",map.toString());
            Log.i("FINAL NUMBER OF ROWS = ",table.getNumberOfRows()+"");
            String pdfPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
            File file = new File(pdfPath,"myPdf.pdf");
            PdfWriter writer = null;
            try {
                writer = new PdfWriter(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            document.add(table);
            document.close();
        }
    };
}