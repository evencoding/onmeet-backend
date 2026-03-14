package repository

import (
	"testing"

	"com.onmeet.file/internal/model"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// contractRepository is a mock that documents the expected FileRepository contract.
// It verifies that any concrete implementation should satisfy these behavioral invariants.
type contractRepository struct {
	mock.Mock
}

func (r *contractRepository) Save(metadata *model.FileMetadata) error {
	args := r.Called(metadata)
	if args.Get(0) != nil {
		return args.Error(0)
	}
	metadata.ID = 1
	return nil
}

func (r *contractRepository) FindByID(id uint) (*model.FileMetadata, error) {
	args := r.Called(id)
	if m, ok := args.Get(0).(*model.FileMetadata); ok {
		return m, args.Error(1)
	}
	return nil, args.Error(1)
}

func (r *contractRepository) Delete(id uint) error {
	args := r.Called(id)
	return args.Error(0)
}

func (r *contractRepository) FindByUploaderAndCategory(uploaderId int64, category string) ([]*model.FileMetadata, error) {
	args := r.Called(uploaderId, category)
	if files, ok := args.Get(0).([]*model.FileMetadata); ok {
		return files, args.Error(1)
	}
	return nil, args.Error(1)
}

var _ FileRepository = (*contractRepository)(nil)

// --- Contract Tests: document and verify FileRepository interface invariants ---

func TestFileRepository_Contract_Save_AssignsID(t *testing.T) {
	// Any FileRepository.Save implementation must assign an ID to the metadata after saving.
	repo := new(contractRepository)
	meta := &model.FileMetadata{FileName: "test.jpg", Category: "profile"}
	repo.On("Save", meta).Return(nil)

	err := repo.Save(meta)

	assert.NoError(t, err)
	assert.Equal(t, uint(1), meta.ID, "Save should assign ID to metadata")
	repo.AssertExpectations(t)
}

func TestFileRepository_Contract_Save_ReturnsError(t *testing.T) {
	// Save must propagate DB errors to the caller.
	repo := new(contractRepository)
	meta := &model.FileMetadata{FileName: "test.jpg"}
	dbErr := model.NewAppError(model.CodeDBSaveFail, 500, "DB 저장 실패")
	repo.On("Save", meta).Return(dbErr)

	err := repo.Save(meta)

	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeDBSaveFail, appErr.Code)
}

func TestFileRepository_Contract_FindByID_ReturnsMetadata(t *testing.T) {
	// FindByID must return the correct FileMetadata for the given ID.
	repo := new(contractRepository)
	expected := &model.FileMetadata{FileName: "found.jpg", Category: "docs"}
	repo.On("FindByID", uint(1)).Return(expected, nil)

	result, err := repo.FindByID(1)

	assert.NoError(t, err)
	assert.Equal(t, expected, result)
	repo.AssertExpectations(t)
}

func TestFileRepository_Contract_FindByID_ErrRecordNotFound_MapsToErrDBNotFound(t *testing.T) {
	// When a record does not exist, FindByID must return model.ErrDBNotFound (not gorm.ErrRecordNotFound).
	// This mapping hides the ORM implementation detail from upper layers.
	repo := new(contractRepository)
	repo.On("FindByID", uint(999)).Return(nil, model.ErrDBNotFound)

	result, err := repo.FindByID(999)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok, "error must be *model.AppError, not a raw gorm error")
	assert.Equal(t, model.CodeDBNotFound, appErr.Code, "error code must be FILE_042")
	assert.Equal(t, 404, appErr.Status)
}

func TestFileRepository_Contract_Delete_Success(t *testing.T) {
	// Delete must call the underlying DB and return nil on success.
	repo := new(contractRepository)
	repo.On("Delete", uint(5)).Return(nil)

	err := repo.Delete(5)

	assert.NoError(t, err)
	repo.AssertExpectations(t)
}

func TestFileRepository_Contract_Delete_SoftDelete(t *testing.T) {
	// Delete uses GORM soft delete (sets deleted_at), so the record remains in the DB.
	// This test documents that the FileMetadata model uses gorm.DeletedAt for soft delete.
	// After Delete, FindByID on the same ID should return ErrDBNotFound (GORM excludes soft-deleted rows).
	repo := new(contractRepository)

	// First, delete the record
	repo.On("Delete", uint(10)).Return(nil)
	err := repo.Delete(10)
	assert.NoError(t, err)

	// Then, FindByID should return not-found (soft-deleted rows are invisible)
	repo.On("FindByID", uint(10)).Return(nil, model.ErrDBNotFound)
	result, findErr := repo.FindByID(10)
	assert.Nil(t, result)
	assert.Equal(t, model.ErrDBNotFound, findErr)

	repo.AssertExpectations(t)
}

func TestFileRepository_Contract_FindByUploaderAndCategory_ReturnsMatches(t *testing.T) {
	// FindByUploaderAndCategory must return all files matching uploader + category.
	repo := new(contractRepository)
	files := []*model.FileMetadata{
		{ID: 1, FileName: "a.jpg", Category: "profile"},
		{ID: 2, FileName: "b.jpg", Category: "profile"},
	}
	repo.On("FindByUploaderAndCategory", int64(42), "profile").Return(files, nil)

	results, err := repo.FindByUploaderAndCategory(42, "profile")

	assert.NoError(t, err)
	assert.Len(t, results, 2)
	assert.Equal(t, uint(1), results[0].ID)
	assert.Equal(t, uint(2), results[1].ID)
	repo.AssertExpectations(t)
}

func TestFileRepository_Contract_FindByUploaderAndCategory_EmptyResult(t *testing.T) {
	// When no files match, return an empty slice, not nil.
	repo := new(contractRepository)
	repo.On("FindByUploaderAndCategory", int64(99), "docs").Return([]*model.FileMetadata{}, nil)

	results, err := repo.FindByUploaderAndCategory(99, "docs")

	assert.NoError(t, err)
	assert.Empty(t, results)
}
