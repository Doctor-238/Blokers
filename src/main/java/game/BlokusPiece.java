package game;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 블로커스 조각(Piece)을 정의하는 클래스.
 * 21가지 모든 조각의 모양, 회전, 좌표 반환을 관리합니다.
 */
public class BlokusPiece {

    public static final String[] ALL_PIECE_IDS = {
            "I1", "I2", "I3", "I4", "I5",
            "L3", "L4", "L5",
            "T4", "T5",
            "O4",
            "Z4", "Z5",
            "F5", "N", "P", "U", "V5", "W", "X", "Y"
    };

    // 조각 모양 원본 데이터 (1=블록, 0=빈칸)
    private static final Map<String, int[][]> SHAPE_DATA = new HashMap<>();
    static {
        SHAPE_DATA.put("I1", new int[][]{{1}});
        SHAPE_DATA.put("I2", new int[][]{{1, 1}});
        SHAPE_DATA.put("I3", new int[][]{{1, 1, 1}});
        SHAPE_DATA.put("I4", new int[][]{{1, 1, 1, 1}});
        SHAPE_DATA.put("I5", new int[][]{{1, 1, 1, 1, 1}});

        SHAPE_DATA.put("L3", new int[][]{{1, 0}, {1, 1}});
        SHAPE_DATA.put("L4", new int[][]{{1, 0, 0}, {1, 1, 1}});
        SHAPE_DATA.put("L5", new int[][]{{1, 0, 0, 0}, {1, 1, 1, 1}});

        SHAPE_DATA.put("T4", new int[][]{{1, 1, 1}, {0, 1, 0}});
        SHAPE_DATA.put("T5", new int[][]{{1, 1, 1}, {0, 1, 0}, {0, 1, 0}});

        SHAPE_DATA.put("O4", new int[][]{{1, 1}, {1, 1}});

        SHAPE_DATA.put("Z4", new int[][]{{1, 1, 0}, {0, 1, 1}});
        SHAPE_DATA.put("Z5", new int[][]{{1, 1, 0}, {0, 1, 0}, {0, 1, 1}});

        SHAPE_DATA.put("F5", new int[][]{{0, 1, 1}, {1, 1, 0}, {0, 1, 0}});
        SHAPE_DATA.put("N", new int[][]{{1, 1, 0, 0}, {0, 1, 1, 1}});
        SHAPE_DATA.put("P", new int[][]{{1, 1}, {1, 1}, {1, 0}});
        SHAPE_DATA.put("U", new int[][]{{1, 0, 1}, {1, 1, 1}});
        SHAPE_DATA.put("V5", new int[][]{{1, 0, 0}, {1, 0, 0}, {1, 1, 1}});
        SHAPE_DATA.put("W", new int[][]{{1, 0, 0}, {1, 1, 0}, {0, 1, 1}});
        SHAPE_DATA.put("X", new int[][]{{0, 1, 0}, {1, 1, 1}, {0, 1, 0}});
        SHAPE_DATA.put("Y", new int[][]{{1, 1, 1, 1}, {0, 1, 0, 0}});
    }

    private String id;
    private int[][] shape;
    private int color;
    private int size; // 이 조각을 구성하는 칸 수

    public BlokusPiece(String id, int color) {
        this.id = id;
        this.color = color;

        int[][] originalShape = SHAPE_DATA.get(id);
        if (originalShape == null) {
            throw new IllegalArgumentException("알 수 없는 조각 ID: " + id);
        }

        this.shape = new int[originalShape.length][originalShape[0].length];
        int calculatedSize = 0;
        for(int i=0; i<originalShape.length; i++) {
            for (int j=0; j<originalShape[i].length; j++) {
                this.shape[i][j] = originalShape[i][j];
                if (originalShape[i][j] == 1) {
                    calculatedSize++;
                }
            }
        }
        this.size = calculatedSize;
    }

    public BlokusPiece(BlokusPiece other) {
        this.id = other.id;
        this.color = other.color;
        this.size = other.size;
        this.shape = new int[other.shape.length][other.shape[0].length];
        for(int i=0; i<other.shape.length; i++) {
            System.arraycopy(other.shape[i], 0, this.shape[i], 0, other.shape[i].length);
        }
    }

    /**
     * 조각을 시계 방향으로 90도 회전시킵니다.
     */
    public void rotate() {
        if (shape == null) return;
        int rows = shape.length;
        int cols = shape[0].length;
        int[][] newShape = new int[cols][rows];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                newShape[c][rows - 1 - r] = shape[r][c];
            }
        }
        this.shape = newShape;
    }

    // [수정됨] 뒤집기(flip) 메소드 제거

    // Getter
    public String getId() { return id; }
    public int[][] getShape() { return shape; }
    public int getColor() { return color; }
    public int getSize() { return size; }
    public int getWidth() { return shape[0].length; }
    public int getHeight() { return shape.length; }

    public List<Point> getPoints() {
        List<Point> points = new ArrayList<>();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 1) {
                    points.add(new Point(c, r)); // (x, y) -> (c, r)
                }
            }
        }
        return points;
    }
}