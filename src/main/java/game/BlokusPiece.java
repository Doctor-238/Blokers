package game;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlokusPiece implements Serializable {

    // [유지] 조각 ID 목록 (게임룸/클라이언트에서 공통 사용)
    public static final String[] ALL_PIECE_IDS = {
            "I1", "I2", "I3", "I4", "I5",
            "L3", "L4", "L5",
            "T4", "T5",
            "O4",
            "Z4", "Z5",
            "F5", "N", "P", "U", "V5", "W", "X", "Y"
    };

    // [유지] 원본 모양 데이터 (1=블록, 0=빈칸)
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
        SHAPE_DATA.put("N",  new int[][]{{1, 1, 0, 0}, {0, 1, 1, 1}});
        SHAPE_DATA.put("P",  new int[][]{{1, 1}, {1, 1}, {1, 0}});
        SHAPE_DATA.put("U",  new int[][]{{1, 0, 1}, {1, 1, 1}});
        SHAPE_DATA.put("V5", new int[][]{{1, 0, 0}, {1, 0, 0}, {1, 1, 1}});
        SHAPE_DATA.put("W",  new int[][]{{1, 0, 0}, {1, 1, 0}, {0, 1, 1}});
        SHAPE_DATA.put("X",  new int[][]{{0, 1, 0}, {1, 1, 1}, {0, 1, 0}});
        SHAPE_DATA.put("Y",  new int[][]{{1, 1, 1, 1}, {0, 1, 0, 0}});
    }

    private String id;
    private int[][] shape; // 회전/반전된 현재 모양
    private int color;     // 1~4
    private int size;      // 블록 칸 개수(점수 계산용)

    /**
     * [생성자] ID/Color로 조각 생성
     * - SHAPE_DATA에서 원본 모양을 deep copy
     * - size(블록 개수) 계산
     */
    public BlokusPiece(String id, int color) {
        this.id = id;
        this.color = color;

        int[][] originalShape = SHAPE_DATA.get(id);
        if (originalShape == null) {
            throw new IllegalArgumentException("알 수 없는 조각 ID: " + id);
        }

        this.shape = new int[originalShape.length][originalShape[0].length];

        int calculatedSize = 0;
        for (int r = 0; r < originalShape.length; r++) {
            for (int c = 0; c < originalShape[r].length; c++) {
                this.shape[r][c] = originalShape[r][c];
                if (originalShape[r][c] == 1) {
                    calculatedSize++;
                }
            }
        }
        this.size = calculatedSize;
    }

    /**
     * [복사 생성자] 회전/반전 등으로 변경되는 객체를 안전하게 복제하기 위해 사용
     * - GameRoom에서 hand의 원본을 보존하고, 배치 검증용으로 복사본을 만들 때 유용
     */
    public BlokusPiece(BlokusPiece other) {
        this.id = other.id;
        this.color = other.color;
        this.size = other.size;

        this.shape = new int[other.shape.length][other.shape[0].length];
        for (int r = 0; r < other.shape.length; r++) {
            System.arraycopy(other.shape[r], 0, this.shape[r], 0, other.shape[r].length);
        }
    }

    /**
     * [회전] 시계 방향 90도
     * - (rows x cols) -> (cols x rows)
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

    /**
     * [반전] 좌우 반전(가로 미러링)
     */
    public void flip() {
        if (shape == null) return;

        int rows = shape.length;
        int cols = shape[0].length;
        int[][] newShape = new int[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                newShape[r][cols - 1 - c] = shape[r][c];
            }
        }
        this.shape = newShape;
    }

    // ===== Getter =====

    public String getId() { return id; }
    public int[][] getShape() { return shape; }
    public int getColor() { return color; }
    public int getSize() { return size; }

    public int getWidth()  { return shape[0].length; }
    public int getHeight() { return shape.length; }

    /**
     * 현재 shape에서 블록(=1)인 좌표만 Point 리스트로 반환
     * - 좌표계: (c, r) = (x, y)
     * - GameRoom에서 보드 배치 검증/적용할 때 사용
     */
    public List<Point> getPoints() {
        List<Point> points = new ArrayList<>();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 1) {
                    points.add(new Point(c, r));
                }
            }
        }
        return points;
    }
}
