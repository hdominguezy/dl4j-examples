package org.deeplearning4j.examples.dataexamples;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.FileStatsStorage;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.util.Random;

/**
 * JavaFX application to show a neural network learning to draw an image.
 * Demonstrates how to feed an NN with externally originated data.
 *
 * This example uses JavaFX, which requires the Oracle JDK. Comment out this example if you use a different JDK.
 * OpenJDK and openjfx have been reported to work fine.
 *
 * @author Robert Altena
 * Many thanks to @tmanthey for constructive feedback and suggestions.
 */
public class ImageDrawer extends Application {

    private Image originalImage; //The source image displayed on the left.
    private WritableImage composition; // Destination image generated by the NN.
    private MultiLayerNetwork nn; // THE nn.
    private INDArray xyOut; //x,y grid to calculate the output image. Needs to be calculated once, then re-used.
    private final Random r = new Random();

    /**
     * Training the NN and updating the current graphical output.
     */
    private void onCalc(){
        int batchSize = 1000;
        int numBatches = 5;
        for (int i =0; i< numBatches; i++){
            DataSet ds = generateDataSet(batchSize);
            nn.fit(ds);
        }
        drawImage();
        Platform.runLater(this::onCalc);
    }

    @Override
    public void init(){
        originalImage = new Image("/DataExamples/Mona_Lisa.png");

        final int w = (int) originalImage.getWidth();
        final int h = (int) originalImage.getHeight();
        composition = new WritableImage(w, h); //Right image.

        nn = createNN();

        boolean fUseUI = false; // set to false if you do not want the web ui to track learning progress.
        if(fUseUI) {
            UIServer uiServer = UIServer.getInstance();
            StatsStorage statsStorage = new FileStatsStorage(new File("java.io.tmpdir"));
            uiServer.attach(statsStorage);
            nn.setListeners(new StatsListener(statsStorage));
        }

        // The x,y grid to calculate the NN output only needs to be calculated once.
        int numPoints = h * w;
        xyOut = Nd4j.zeros(numPoints, 2);
        for (int i = 0; i < w; i++) {
            double xp = scaleXY(i,w);
            for (int j = 0; j < h; j++) {
                int index = i + w * j;
                double yp = scaleXY(j,h);

                xyOut.put(index, 0, xp); //2 inputs. x and y.
                xyOut.put(index, 1, yp);
            }
        }
        drawImage();
    }
    /**
     * Standard JavaFX start: Build the UI, display
     */
    @Override
    public void start(Stage primaryStage) {

        final int w = (int) originalImage.getWidth();
        final int h = (int) originalImage.getHeight();
        final int zoom = 1; // Our images are a tad small, display them enlarged to have something to look at.

        ImageView iv1 = new ImageView(); //Left image
        iv1.setImage(originalImage);
        iv1.setFitHeight( zoom* h);
        iv1.setFitWidth(zoom*w);

        ImageView iv2 = new ImageView();
        iv2.setImage(composition);
        iv2.setFitHeight( zoom* h);
        iv2.setFitWidth(zoom*w);

        HBox root = new HBox(); //build the scene.
        Scene scene = new Scene(root);
        root.getChildren().addAll(iv1, iv2);

        primaryStage.setTitle("Neural Network Drawing Demo.");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(we -> System.exit(0));
        primaryStage.show();

        Platform.setImplicitExit(true);

        //Allow JavaFX do to it's thing, Initialize the Neural network when it feels like it.
        Platform.runLater(this::onCalc);
    }

    public static void main( String[] args )
    {
        launch(args);
    }

    /**
     * Build the Neural network.
     */
    private static MultiLayerNetwork createNN() {
        int seed = 2345;
        double learningRate = 0.05;
        int numInputs = 2;   // x and y.
        int numHiddenNodes = 100;
        int numOutputs = 3 ; //R, G and B value.

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(seed)
            .weightInit(WeightInit.XAVIER)
            .updater(new Nesterovs(learningRate, 0.9))
            .list()
            .layer(new DenseLayer.Builder().nIn(numInputs).nOut(numHiddenNodes)
                .activation(Activation.LEAKYRELU)
                .build())
            .layer(new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                .activation(Activation.LEAKYRELU)
                .build())
            .layer(new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                .activation(Activation.LEAKYRELU)
                .build())
            .layer(new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                .activation(Activation.LEAKYRELU)
                .build())
            .layer(new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                .activation(Activation.LEAKYRELU)
                .build())
            .layer(new OutputLayer.Builder(LossFunctions.LossFunction.L2)
                .activation(Activation.IDENTITY)
                .nIn(numHiddenNodes).nOut(numOutputs).build())
            .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        return net;
    }

    /**
     * Process a javafx Image to be consumed by DeepLearning4J.
     *
     * @param batchSize number of sample points to take out of the image.
     * @return DeepLearning4J DataSet.
     */
    private  DataSet generateDataSet(int batchSize) {
        int w = (int) originalImage.getWidth();
        int h = (int) originalImage.getHeight();

        PixelReader reader = originalImage.getPixelReader();

        INDArray xy = Nd4j.zeros(batchSize, 2);
        INDArray out = Nd4j.zeros(batchSize, 3);

        for (int index = 0; index < batchSize; index++) {
            int i = r.nextInt(w);
            int j = r.nextInt(h);
            double xp = scaleXY(i,w);
            double yp = scaleXY(j,h);
            Color c = reader.getColor(i, j);

            xy.put(index, 0, xp); //2 inputs. x and y.
            xy.put(index, 1, yp);

            out.put(index, 0, c.getRed());  //3 outputs. the RGB values.
            out.put(index, 1, c.getGreen());
            out.put(index, 2, c.getBlue());
        }
        return new DataSet(xy, out);
    }

    /**
     * Make the Neural network draw the image.
     */
    private void drawImage() {
        int w = (int) composition.getWidth();
        int h = (int) composition.getHeight();

        INDArray out = nn.output(xyOut);
        PixelWriter writer = composition.getPixelWriter();

        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int index = i + w * j;
                double red = capNNOutput(out.getDouble(index, 0));
                double green = capNNOutput(out.getDouble(index, 1));
                double blue = capNNOutput(out.getDouble(index, 2));

                Color c = new Color(red, green, blue, 1.0);
                writer.setColor(i, j, c);
            }
        }
    }

    /**
     * Make sure the color values are >=0 and <=1
     */
    private static double capNNOutput(double x) {
        double tmp = (x<0.0) ? 0.0 : x;
        return (tmp > 1.0) ? 1.0 : tmp;
    }

    /**
     * scale x,y points
     */
    private static double scaleXY(int i, int maxI){
        return (double) i / (double) (maxI - 1) -0.5;
    }
}
