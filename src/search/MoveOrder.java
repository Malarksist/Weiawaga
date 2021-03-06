package search;

import evaluation.EConstants;
import evaluation.Evaluation;
import movegen.*;

import java.util.Collections;
import static java.lang.Integer.min;


public class MoveOrder {

    private static final int[][][] killerMoves = new int[2][1000][1];
    private static final int[][] historyMoves = new int[64][64];
    private static final int[][] MvvLvaScores = new int[6][6];

    private static final int HashMoveScore = 10000;
    private static final int PromotionScore = 5000;
    private static final int CaptureScore = 200;
    private static final int KillerMoveScore = 90;

    static {
        final int[] VictimScore = {100, 200, 300, 400, 500, 600};
        for (int attacker = PieceType.PAWN; attacker <= PieceType.KING; attacker++) {
            for (int victim = PieceType.PAWN; victim <= PieceType.KING; victim++) {
                MvvLvaScores[victim][attacker] = VictimScore[victim] + 6 - (VictimScore[attacker] / 100);
            }
        }
    }

    public static int seeCapture(Board board, Move move){
        int capturedPieceType;
        if (move.flags() == Move.EN_PASSANT)
            capturedPieceType = PieceType.PAWN;
        else
            capturedPieceType = board.pieceTypeAt(move.to());
        board.push(move);
        int value = 0;
        if (move.isPromotion())
            switch(move.flags()){
                case Move.PC_QUEEN:
                    value = EConstants.PIECE_TYPE_VALUES[PieceType.QUEEN] + EConstants.PIECE_TYPE_VALUES[capturedPieceType] - see(board, move.to());
                    break;
                case Move.PC_ROOK:
                    value = EConstants.PIECE_TYPE_VALUES[PieceType.ROOK] + EConstants.PIECE_TYPE_VALUES[capturedPieceType] - see(board, move.to());
                    break;
                case Move.PC_BISHOP:
                    value = EConstants.PIECE_TYPE_VALUES[PieceType.BISHOP] + EConstants.PIECE_TYPE_VALUES[capturedPieceType] - see(board, move.to());
                    break;
                case Move.PC_KNIGHT:
                    value = EConstants.PIECE_TYPE_VALUES[PieceType.KNIGHT] + EConstants.PIECE_TYPE_VALUES[capturedPieceType] - see(board, move.to());
                    break;
                case Move.CAPTURE:
                case Move.EN_PASSANT:
                    value = EConstants.PIECE_TYPE_VALUES[capturedPieceType] - see(board, move.to());
            }
        board.pop();

        return Score.eval(value, board.phase());
    }

    public static int see(Board board, int toSq){
        int value = 0;
        int fromSq = board.smallestAttacker(toSq, board.getSideToPlay());
        if (fromSq != Square.NO_SQUARE){
            int capturedPieceValue = Score.eval(EConstants.PIECE_TYPE_VALUES[board.pieceTypeAt(toSq)], board.phase());
            board.push(new Move(fromSq, toSq, Move.CAPTURE));
            value = Math.max(0, capturedPieceValue - see(board, toSq));
            board.pop();
        }
        return value;
    }

    public static void addKiller(Board board, Move move, int ply){
        int side = board.getSideToPlay();
        for (int i = killerMoves[side][ply].length - 2; i >= 0; i--)
            killerMoves[side][ply][i+1] = killerMoves[side][ply][i];
        killerMoves[side][ply][0] = move.move();
    }

    public static boolean isKiller(Board board, Move move, int ply){
        int moveInt = move.move();
        int side = board.getSideToPlay();
        for (int i = 0; i < killerMoves[side][ply].length; i++){
            if (moveInt == killerMoves[side][ply][i])
                return true;
        }
        return false;
    }

    public static void clearKillers(){
        for (int color = Side.WHITE; color <= Side.BLACK; color++){
            for (int ply = 0; ply < killerMoves[0].length; ply++){
                for (int killer_i = 0; killer_i < killerMoves[0][0].length; killer_i++){
                    killerMoves[color][ply][killer_i] = 0;
                }
            }
        }
    }

    public static void addHistory(Move move, int depth){
        int from = move.from();
        int to = move.to();
        historyMoves[from][to] += depth*depth;

        if (historyMoves[from][to] > Integer.MAX_VALUE / 2) {
            for (int sq1 = Square.A1; sq1 <= Square.H8; sq1++){
                for (int sq2 = Square.A1; sq2 <= Square.H8; sq2++){
                    historyMoves[sq1][sq2] /= 2;
                }
            }
        }

    }

    public static int getHistoryValue(Move move){
        return historyMoves[move.from()][move.to()];
    }

    public static void clearHistory(){
        for (int sq1 = Square.A1; sq1 <= Square.H8; sq1++){
            for (int sq2 = Square.A1; sq2 <= Square.H8; sq2++){
                historyMoves[sq1][sq2] = 0;
            }
        }
    }

    public static void ageHistory(){
        for (int sq1 = Square.A1; sq1 <= Square.H8; sq1++){
            for (int sq2 = Square.A1; sq2 <= Square.H8; sq2++){
                historyMoves[sq1][sq2] /= 8;
            }
        }
    }

    public static int getMvvLvaScore(Board board, Move move){
        return MvvLvaScores[board.pieceTypeAt(move.to())][board.pieceTypeAt(move.from())];
    }

    public static void scoreMoves(final Board board, final MoveList moves, int ply) {

        if (moves.size() == 0)
            return;

        Move hashMove = null;
        TTEntry ttEntry = TranspTable.probe(board.hash());
        if (ttEntry != null) {
            hashMove = ttEntry.move();
        }

        for (Move move : moves) {
            if (move.equals(hashMove)) {
                move.addToScore(HashMoveScore);
            }
            if (isKiller(board, move, ply)) {
                move.addToScore(KillerMoveScore);
            }
            switch (move.flags()) {
                case Move.PC_BISHOP:
                case Move.PC_KNIGHT:
                case Move.PC_ROOK:
                case Move.PC_QUEEN:
                    move.addToScore(CaptureScore);
                    move.addToScore(getMvvLvaScore(board, move));
                case Move.PR_BISHOP:
                case Move.PR_KNIGHT:
                case Move.PR_ROOK:
                case Move.PR_QUEEN:
                    move.addToScore(PromotionScore);
                    break;
                case Move.CAPTURE:
                    move.addToScore(CaptureScore);
                    move.addToScore(getMvvLvaScore(board, move));
                    break;
                case Move.QUIET:
                case Move.EN_PASSANT:
                case Move.DOUBLE_PUSH:
                case Move.OO:
                case Move.OOO:
                    move.addToScore(min(getHistoryValue(move), KillerMoveScore));
                    break;
            }
        }
    }

    public static void sortNextBestMove(MoveList moves, int curIndex){
        int max = Integer.MIN_VALUE;
        int maxIndex = -1;
        for (int i = curIndex; i < moves.size(); i++){
            if (moves.get(i).score() > max){
                max = moves.get(i).score();
                maxIndex = i;
            }
        }
        Collections.swap(moves, curIndex, maxIndex);
    }

}
